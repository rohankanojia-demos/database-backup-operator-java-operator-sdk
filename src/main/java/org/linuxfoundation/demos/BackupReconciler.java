package org.linuxfoundation.demos;

import demos.linuxfoundation.org.v1alpha1.Backup;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import io.quarkus.runtime.util.StringUtil;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Base64;

@ControllerConfiguration
@ApplicationScoped
public class BackupReconciler implements Reconciler<Backup> {
  private static final String BACKUP_FILE_TIME_FORMAT = "yyyy.MM.dd.HH.mm.ss";
  private static final String DEFAULT_SCHEDULE = "0/3 * * * * ?";
  private static final Logger logger = LoggerFactory.getLogger(BackupReconciler.class.getSimpleName());

  @Inject
  Scheduler scheduler;

  @Override
  public UpdateControl<Backup> reconcile(final Backup backup, Context<Backup> context) throws Exception {
    // Fetch the database password from the Kubernetes Secret
    logger.info("Checking Secret exists Kubernetes Cluster");
    Secret secret = context.getClient().secrets()
      .inNamespace(backup.getMetadata().getNamespace())
      .withName(backup.getSpec().getDatabase().getPasswordSecret())
      .get();
    if (secret == null) {
      logger.error("Unable to find Secret {} in Kubernetes Cluster", backup.getSpec().getDatabase().getPasswordSecret());
    }
    logger.info("Fetched Secret {}", secret.getMetadata().getName());
    String dbPassword = secret.getData().get("POSTGRES_PASSWORD");
    dbPassword = new String(Base64.getDecoder().decode(dbPassword.getBytes(StandardCharsets.UTF_8)));

    // Schedule the backup job
    scheduleBackup(backup, dbPassword);

    // Logic to handle retention policy
    handleRetention(backup);

    return UpdateControl.noUpdate();

  }

  private void scheduleBackup(Backup backup, String dbPassword) {
    String schedule = backup.getSpec().getSchedule();
    if (StringUtil.isNullOrEmpty(schedule)) {
      schedule = DEFAULT_SCHEDULE;
    }
    logger.info("Scheduled job as per {}", schedule);

    if (scheduler.getScheduledJob(backup.getMetadata().getName()) != null) {
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
    // Logic to delete old backups based on the retention policy
  }

  @Scheduled(every = "10m")
  public void noOp() {
    // NOOP
  }
}
