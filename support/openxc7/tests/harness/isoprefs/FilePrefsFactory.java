package isoprefs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** Keeps java.util.prefs in one properties file (-Disoprefs.file=...) instead of the macOS user defaults. */
public class FilePrefsFactory implements PreferencesFactory {
  private static FilePrefs root;

  public synchronized Preferences userRoot() {
    if (root == null) root = new FilePrefs(null, "");
    return root;
  }

  public Preferences systemRoot() {
    return userRoot();
  }

  static final class FilePrefs extends AbstractPreferences {
    private static final File FILE = new File(System.getProperty("isoprefs.file", "isoprefs.properties"));
    private static final Properties STORE = new Properties();

    static {
      try (var in = new FileInputStream(FILE)) {
        STORE.load(in);
      } catch (IOException e) {
        // start empty
      }
    }

    FilePrefs(AbstractPreferences parent, String name) {
      super(parent, name);
    }

    private String key(String k) {
      return absolutePath() + "|" + k;
    }

    private static synchronized void save() {
      try (var out = new FileOutputStream(FILE)) {
        STORE.store(out, "isolated logisim-evolution preferences");
      } catch (IOException e) {
        // ignore
      }
    }

    @Override protected void putSpi(String k, String v) { STORE.setProperty(key(k), v); save(); }
    @Override protected String getSpi(String k) { return STORE.getProperty(key(k)); }
    @Override protected void removeSpi(String k) { STORE.remove(key(k)); save(); }
    @Override protected void removeNodeSpi() { }
    @Override protected AbstractPreferences childSpi(String name) { return new FilePrefs(this, name); }
    @Override protected void syncSpi() { }
    @Override protected void flushSpi() { save(); }
    @Override protected String[] childrenNamesSpi() { return new String[0]; }

    @Override
    protected String[] keysSpi() {
      final var prefix = absolutePath() + "|";
      final var keys = new ArrayList<String>();
      for (final var k : STORE.stringPropertyNames()) if (k.startsWith(prefix)) keys.add(k.substring(prefix.length()));
      return keys.toArray(new String[0]);
    }
  }
}
