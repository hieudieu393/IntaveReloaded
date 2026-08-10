/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.connect.IntaveDomains;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.security.HWIDVerification;
import de.jpx3.intave.security.LicenseAccess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;

public final class Sample {
  private String id;
  private Resource resource;

  public Sample() {
  }

  @Deprecated
  public Resource resource() {
    if (resource == null) {
      resource = writableSampleResource();
      if (!resource.writeStreamSupported()) {
        throw new IntaveInternalException("Sample resource does not support writing!");
      }
    }
    return resource;
  }

  public String id() {
    return id;
  }

  @Deprecated
  public long uploadAndDelete() throws IOException {
    if (resource == null) {
      return 0;
    }
    URL url = new URL("https://" + IntaveDomains.primaryServiceDomain() + "/samples/upload");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setDoOutput(true);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/zip");
    connection.setRequestProperty("Identifier", LicenseAccess.rawLicense());
    connection.setRequestProperty("Hardware", HWIDVerification.publicHardwareIdentifier());
    connection.setRequestProperty("User-Agent", "Intave/" + IntavePlugin.fullVersion());
    long length = 0;
    try (
      InputStream read = new DeflaterInputStream(resource.read(), new Deflater(Deflater.BEST_COMPRESSION));
      OutputStream outputStream = connection.getOutputStream()
    ) {
      int count;
      byte[] buffer = new byte[8192];
      while ((count = read.read(buffer)) != -1) {
        outputStream.write(buffer, 0, count);
        length += count;
      }
    }
    InputStream inputStream = connection.getInputStream();
    StringBuilder response = new StringBuilder();
    Scanner scanner = new Scanner(inputStream);
    while (scanner.hasNextLine()) {
      response.append(scanner.nextLine());
    }
    scanner.close();
    if (!"SUCCESS".contentEquals(response)) {
      throw new RuntimeException("Server error: " + response);
    }
    delete();
    return length;
  }

  public void delete() {
    if (resource != null) {
      resource.delete();
      resource = null;
    }
  }

  @Deprecated
  private Resource writableSampleResource() {
    File dataFolder = new File(IntavePlugin.singletonInstance().dataFolder(), "samples");
    if (!dataFolder.exists()) {
      if (!dataFolder.mkdirs()) {
        throw new IntaveInternalException("Unable to create sample folder " + dataFolder);
      }
    }
    File sampleFile;
    do {
      String datetime = String.valueOf(System.currentTimeMillis());
      sampleFile = new File(dataFolder, (id = randomId()) + "." + datetime + ".sample");
    } while (sampleFile.exists());
    return Resources.resourceFromFile(sampleFile)/*.compressed()*/.locked(sampleFile);
  }

  @Deprecated
  private static String randomId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  @Deprecated
  private static File sampleFolder() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/Samples/";
    } else {
      filePath = System.getProperty("user.home") + "/.intave/samples/";
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return workDirectory;
  }
}
