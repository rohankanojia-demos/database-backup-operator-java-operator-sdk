package org.linuxfoundation.demos;

import demos.linuxfoundation.org.v1alpha1.Backup;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

@ControllerConfiguration
@ApplicationScoped
public class BackupReconciler implements Reconciler<Backup>, Cleaner<Backup> {
  private static final String BACKUP_FILE_TIME_FORMAT = "yyyy.MM.dd.HH.mm.ss";
  private static final String BACKUP_CLEANUP_SCHEDULE = "0 15 10 * * ?";
  private static final Logger logger = LoggerFactory.getLogger(BackupReconciler.class.getSimpleName());

  @Inject
  Scheduler scheduler;

  @Override
  public UpdateControl<Backup> reconcile(final Backup backup, Context<Backup> context) throws Exception {
    // Fetch the database password from the Kubernetes Secret
    String dbPassword = getDbPasswordFromKubernetesSecret(backup, context);
    // Create/Update schedule for the backup job
    scheduleBackup(backup, dbPassword);

    // Logic to handle retention policy
    handleRetention(backup);

    return UpdateControl.noUpdate();
  }

  private static String getDbPasswordFromKubernetesSecret(Backup backup, Context<Backup> context) {
    Secret secret = context.getClient().secrets()
      .inNamespace(backup.getMetadata().getNamespace())
      .withName(backup.getSpec().getDatabase().getPasswordSecret())
      .get();
    if (secret == null) {
      logger.error("Unable to find Secret {} in Kubernetes Cluster", backup.getSpec().getDatabase().getPasswordSecret());
      return null;
    }
    logger.info("Fetched Secret {}", secret.getMetadata().getName());
    String dbPassword = secret.getData().get("POSTGRES_PASSWORD");
    dbPassword = new String(Base64.getDecoder().decode(dbPassword.getBytes(StandardCharsets.UTF_8)));
    return dbPassword;
  }

  private void scheduleBackup(Backup backup, String dbPassword) {
    String schedule = backup.getSpec().getSchedule();
    logger.info("Scheduled job as per {}", schedule);

    if (scheduler.getScheduledJob(backup.getMetadata().getName()) != null) {
      logger.info("Deleting previous schedule for {}", backup.getMetadata().getName());
      scheduler.unscheduleJob(backup.getMetadata().getName());
    }
    scheduler.newJob(backup.getMetadata().getName())
        .setCron(schedule)
        .setTask(executionContext -> performBackup(backup, dbPassword))
        .schedule();
  }

  private void performBackup(Backup backup, String dbPassword) {
    logger.info("Connecting to database {}/{}", backup.getSpec().getDatabase().getHost(), backup.getSpec().getDatabase().getName());
    try (Connection conn = DriverManager.getConnection(
      "jdbc:postgresql://" + backup.getSpec().getDatabase().getHost() + ":" + backup.getSpec().getDatabase().getPort() + "/" + backup.getSpec().getDatabase().getName(),
      backup.getSpec().getDatabase().getUser(),
      dbPassword)) {
      logger.info("Success");
      File backupFile = Paths.get(backup.getSpec().getStorageLocation(), backup.getMetadata().getName() + "-" + new SimpleDateFormat(BACKUP_FILE_TIME_FORMAT).format(new java.util.Date()) + ".csv").toFile();
      String backupCommand = String.format("COPY (SELECT * FROM %s) TO '%s' WITH (FORMAT csv)",
        backup.getSpec().getDatabase().getTable(),
        backupFile.getAbsolutePath());

      try (Statement stmt = conn.createStatement()) {
        logger.info("Taking backup...");
        boolean executedSuccessfully = stmt.execute(backupCommand);
        if (executedSuccessfully) {
          logger.info("Success");
        }
      }
    } catch (Exception e) {
      logger.error("failure in taking backup", e);
    }
  }

  private void handleRetention(Backup backup) {
    String retentionJobName = String.format("%s-retention", backup.getMetadata().getName());
    if (scheduler.getScheduledJob(retentionJobName) != null) {
      scheduler.unscheduleJob(retentionJobName);
    }
    scheduler.newJob(retentionJobName)
      .setCron(BACKUP_CLEANUP_SCHEDULE)
      .setTask(executionContext -> cleanUpOldFiles(backup))
      .schedule();
  }

  private void cleanUpOldFiles(Backup backup) {
    logger.info("Cleaning up old files for {}", backup.getMetadata().getName());
    Path backupDirPath = Paths.get(backup.getSpec().getStorageLocation());
    if (backupDirPath.toFile().isDirectory()) {
      try (Stream<Path> files = Files.list(backupDirPath)
        .filter(p -> p.toFile().getName().startsWith(backup.getMetadata().getName()))) {
        Instant now = Instant.now();

        List<Path> backupFiles = files.toList();
        logger.info("{} backup files found", backupFiles.size());
        for (Path backupFilePath : backupFiles) {
          BasicFileAttributes attrs = Files.readAttributes(backupFilePath, BasicFileAttributes.class);
          Instant fileTime = attrs.lastModifiedTime().toInstant();
          long ageInDays = ChronoUnit.DAYS.between(fileTime, now);
          logger.info("{} age in days", ageInDays);
          if (ageInDays >= backup.getSpec().getRetention()) {
            Files.delete(backupFilePath);
            logger.info("Deleted: {}", backupFilePath.getFileName());
          }
        }
      } catch (IOException ioException) {
        logger.error("error while performing cleanup", ioException);
      }
    }
  }

  @Scheduled(every = "10m")
  public void noOp() {
    // NOOP
  }

  @Override
  public DeleteControl cleanup(Backup resource, Context<Backup> context) {
    logger.info("Deletion of Backup with name {} detected", resource.getMetadata().getName());
    logger.info("Removing scheduled job {}", resource.getMetadata().getName());
    scheduler.unscheduleJob(resource.getMetadata().getName());
    return DeleteControl.defaultDelete();
  }
}
