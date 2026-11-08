/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import com.cburch.logisim.prefs.AppPreferences;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;

public final class JFileChoosers {

  private static final String[] PROP_NAMES = {
    null, "user.home", "user.dir", "java.home", "java.io.tmpdir"
  };

  private static String currentDirectory = "";

  /** Where Windows lists the user's WSL distributions, a subkey with a DistributionName each. */
  private static final String WSL_REGISTRY_KEY =
      "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss";

  /** A DistributionName in reg.exe's output; WSL allows letters, digits, '.', '_' and '-'. */
  private static final Pattern WSL_DISTRIBUTION =
      Pattern.compile("^\\s*DistributionName\\s+REG_SZ\\s+([A-Za-z0-9._-]+)\\s*$",
          Pattern.MULTILINE);

  /** The WSL distributions' home folders and names, found when the first file chooser opens. */
  private static Map<File, String> wslHomes;

  /** Their icon in the shortcuts: WSL's own (getWslIcon). */
  private static Icon wslIcon;

  private JFileChoosers() {
    throw new IllegalStateException("Utility class. No instantiation allowed.");
  }

  /*
   * A user reported that JFileChooser's constructor sometimes resulted in
   * IOExceptions when Logisim is installed under a system administrator
   * account and then is attempted to run as a regular user. This class is an
   * attempt to be a bit more robust about which directory the JFileChooser
   * opens up under. (23 Feb 2010)
   */
  private static class LogisimFileChooser extends JFileChooser {
    private static final long serialVersionUID = 1L;

    LogisimFileChooser() {
      super();
    }

    LogisimFileChooser(File initSelected) {
      super(initSelected);
    }

    @Override
    public File getSelectedFile() {
      final var dir = getCurrentDirectory();
      if (dir != null) {
        JFileChoosers.currentDirectory = dir.toString();
      }
      return super.getSelectedFile();
    }
  }

  public static JFileChooser create() {
    RuntimeException first = null;
    for (final var prop : PROP_NAMES) {
      try {
        String dirname;
        if (prop == null) {
          dirname = currentDirectory;
          if ("".equals(dirname)) {
            dirname = AppPreferences.DIALOG_DIRECTORY.get();
          }
        } else {
          dirname = System.getProperty(prop);
        }
        if ("".equals(dirname)) {
          return new LogisimFileChooser();
        } else {
          final var dir = new File(dirname);
          if (dir.canRead()) {
            return new LogisimFileChooser(dir);
          }
        }
      } catch (NullPointerException e) {
        // macOS JDK bug in getChooserShortcutPanelFiles - try next directory
        if (first == null) first = e;
      } catch (RuntimeException t) {
        if (first == null) first = t;
        final var u = t.getCause();
        if (!(u instanceof IOException)) throw t;
      }
    }
    // Last resort: try creating without any directory
    try {
      return new LogisimFileChooser();
    } catch (Exception e) {
      if (first != null) throw first;
      throw new RuntimeException("Unable to create file chooser", e);
    }
  }

  public static JFileChooser createAt(File openDirectory) {
    if (openDirectory == null) {
      return create();
    } else {
      try {
        return new LogisimFileChooser(openDirectory);
      } catch (RuntimeException t) {
        // Handle IOException cause or NullPointerException (macOS JDK bug)
        if (t.getCause() instanceof IOException || t instanceof NullPointerException) {
          try {
            return create();
          } catch (RuntimeException ignored) {
            return new LogisimFileChooser();
          }
        }
        throw t;
      }
    }
  }

  public static JFileChooser createSelected(File selected) {
    if (selected == null) {
      return create();
    } else if (selected.isDirectory()) {
      return createAt(selected);
    } else {
      final var ret = createAt(selected.getParentFile());
      ret.setSelectedFile(selected);
      return ret;
    }
  }

  public static String getCurrentDirectory() {
    return currentDirectory;
  }

  /**
   * On Windows, adds the user's WSL distributions to the shortcuts on the left of the file
   * chooser, each as its home folder ({@code \\wsl.localhost\<name>\home}). The file chooser cannot
   * browse to Linux's files by itself, as File Explorer can, and a circuit kept there (LogOS's
   * {@code computer.circ}) is then a click away. The distributions, whichever the user has, are
   * read from the registry when the first file chooser opens; WSL itself is reached, and started
   * if need be, only when one of these shortcuts is clicked. Elsewhere this does nothing.
   */
  public static void addWslShortcuts() {
    if (!System.getProperty("os.name", "").startsWith("Windows")) {
      return;
    }
    // FlatLaf's shortcuts panel: names and icons of our own keep it from asking the shell about
    // the WSL folders themselves, which would start WSL
    UIManager.put("FileChooser.shortcuts.filesFunction",
        (Function<File[], File[]>) JFileChoosers::withWslHomes);
    UIManager.put("FileChooser.shortcuts.displayNameFunction",
        (Function<File, String>) file -> wslHomes().get(file));
    UIManager.put("FileChooser.shortcuts.iconFunction",
        (Function<File, Icon>) file -> wslHomes().containsKey(file) ? getWslIcon() : null);
  }

  private static File[] withWslHomes(File[] shortcuts) {
    final var homes = wslHomes();
    if (homes.isEmpty()) {
      return shortcuts;
    }
    final var all = new ArrayList<File>();
    if (shortcuts != null) {
      all.addAll(Arrays.asList(shortcuts));
    }
    all.addAll(homes.keySet());
    return all.toArray(new File[0]);
  }

  /**
   * WSL's own icon, the penguin File Explorer shows for Linux: that of wsl.exe, which Windows reads
   * from the file without starting WSL (the Store's WSL first, then Windows' own). FlatLaf's drive
   * icon if neither is there.
   */
  private static synchronized Icon getWslIcon() {
    if (wslIcon == null) {
      final var env = System.getenv();
      final var programs = List.of(
          new File(env.getOrDefault("ProgramFiles", "C:\\Program Files"), "WSL\\wsl.exe"),
          new File(env.getOrDefault("SystemRoot", "C:\\Windows"), "System32\\wsl.exe"));
      for (final var exe : programs) {
        if (exe.isFile()) {
          wslIcon = FileSystemView.getFileSystemView().getSystemIcon(exe, 32, 32);
          break;
        }
      }
      if (wslIcon == null) {
        wslIcon = UIManager.getIcon("FileView.hardDriveIcon");
      }
    }
    return wslIcon;
  }

  private static synchronized Map<File, String> wslHomes() {
    if (wslHomes == null) {
      wslHomes = new LinkedHashMap<>();
      for (final var name : wslDistributions(queryWslRegistry())) {
        wslHomes.put(new File("\\\\wsl.localhost\\" + name + "\\home"), name);
      }
    }
    return wslHomes;
  }

  /** What reg.exe says about the distributions' names, or "" when it says nothing (no WSL). */
  private static String queryWslRegistry() {
    final var reg = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"),
        "System32", "reg.exe");
    try {
      final var process = new ProcessBuilder(reg.toString(), "query", WSL_REGISTRY_KEY, "/s",
          "/v", "DistributionName").redirectErrorStream(true).start();
      process.getOutputStream().close();
      final var output =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
      return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0 ? output : "";
    } catch (IOException e) {
      return "";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "";
    }
  }

  /**
   * The WSL distributions named in the output of {@code reg query} for the DistributionName
   * values, each once, leaving out Docker Desktop's and Rancher Desktop's own.
   */
  static List<String> wslDistributions(String regOutput) {
    final var names = new ArrayList<String>();
    final var matcher = WSL_DISTRIBUTION.matcher(regOutput);
    while (matcher.find()) {
      final var name = matcher.group(1);
      if (!name.startsWith("docker-desktop") && !name.startsWith("rancher-desktop")
          && !names.contains(name)) {
        names.add(name);
      }
    }
    return names;
  }
}
