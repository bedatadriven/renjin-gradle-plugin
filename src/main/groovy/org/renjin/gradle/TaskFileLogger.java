package org.renjin.gradle;

import org.gradle.api.Task;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class TaskFileLogger {

  private Writer writer;
  private final FileOutputStream output;
  private boolean infoEnabled;
  private boolean closed;
  private final File logFile;

  public TaskFileLogger(Task task) throws FileNotFoundException {
    logFile = new File(task.getProject().getBuildDir(), task.getName() + ".log");
    logFile.getParentFile().mkdirs();

    infoEnabled = task.getLogger().isInfoEnabled();
    output = new FileOutputStream(logFile);
    writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);

  }

  public OutputStream getStandardOutput() {
    return createDuplexStream(System.out);
  }

  public OutputStream getErrorOutput() {
    return createDuplexStream(System.err);
  }

  private OutputStream createDuplexStream(final PrintStream echo) {
    return new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        if(infoEnabled) {
          echo.write(b);
        }
        writer.flush();
        output.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        if(infoEnabled) {
          echo.write(b, off, len);
        }
        writer.flush();
        output.write(b, off, len);
      }

      @Override
      public void flush() throws IOException {
        if(infoEnabled) {
          echo.flush();
        }
        writer.flush();
        output.flush();
      }
    };
  }

  public void close() {
    closed = true;
    try {
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
