package org.linuxfoundation.demos;

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
}
