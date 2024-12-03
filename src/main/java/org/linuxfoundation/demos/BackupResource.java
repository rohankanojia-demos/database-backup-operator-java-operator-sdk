package org.linuxfoundation.demos;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import demos.linuxfoundation.org.v1alpha1.Backup;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


@Path("/backup")
public class BackupResource {
  @ConfigProperty(name = "backup.path")
  String folderPath;

  @GET
  @Path("/list")
  public Response listFolderContents() {
    File folder = new File(folderPath);

    if (!folder.exists() || !folder.isDirectory()) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("Folder not found: " + folderPath)
        .build();
    }

    // List the folder contents
    File[] files = folder.listFiles();
    if (files == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity("Unable to read folder: " + folderPath)
        .build();
    }

    List<String> fileNames = new ArrayList<>();
    for (File file : files) {
      fileNames.add(file.getName());
    }

    return Response.ok(fileNames).build();
  }

  @GET
  @Path("/list-cr")
  public Response listBackupCustomResources() {
    try (KubernetesClient kubernetesClient = new KubernetesClientBuilder().build()) {
      List<String> backupCustomResourceList = kubernetesClient.resources(Backup.class)
        .inNamespace("default")
        .list()
        .getItems()
        .stream()
        .map(CustomResource::getMetadata)
        .map(ObjectMeta::getName)
        .toList();
      return Response.ok(backupCustomResourceList).build();
    }
  }
}
