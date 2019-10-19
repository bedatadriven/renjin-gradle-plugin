package org.renjin.gradle;

import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.StandardOutputListener;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class TaskFileLogger implements StandardOutputListener {

  private Writer writer;
  private final FileOutputStream output;
  private boolean infoEnabled;

  public TaskFileLogger(Task task) throws FileNotFoundException {
    File logFile = new File(task.getProject().getBuildDir(), task.getName() + ".log");
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

  @Override
  public void onOutput(CharSequence output) {
    try {
      writer.append(output);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void close() {
    try {
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
