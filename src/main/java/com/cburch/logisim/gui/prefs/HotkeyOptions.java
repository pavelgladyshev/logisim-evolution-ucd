/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.prefs;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.prefs.PrefMonitorKeyStroke;
import com.cburch.logisim.util.JAdjustableScroll;
import com.cburch.logisim.util.JHotkeyInput;
import com.cburch.logisim.util.TableLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

class HotkeyOptions extends OptionsPanel {
  private static final long serialVersionUID = 1L;
  /*
   * Hotkey Options TAB
   *
   * Author: Hanyuan Zhao <2524395907@qq.com>
   *
   * Description:
   * This is the hotkey settings Tab in the preferences.
   * Allowing users to decide which hotkey to bind to the specific function.
   *
   * To add your own hotkey bindings from your code, you need some operations as follows.
   * Firstly add your hotkey configurations to AppPreferences and set up their strings in resources
   * Fill the resetHotkeys method in AppPreferences, adding the reset code for your hotkeys
   * Set up the hotkey in your code by accessing AppPreferences.HOTKEY_ADD_BY_YOU
   * Do not forget to sync with the user's settings.
   * You should go modifying hotkeySync in AppPreferences, adding your codes there.
   *
   * Now the hotkey options don't involve all the bindings in logisim.
   * The hotkeys chosen by the user might have conflict with
   * some build-in key bindings until all key bindings can be set in this tab.
   * TODO: If you are available, you can bind them in order to make logisim feel better
   *
   * */
  @SuppressWarnings("unchecked")
  protected static List<PrefMonitor<KeyStroke>> hotkeys = new ArrayList<>();
  private final List<JHotkeyInput> keyInputList;
  private final JLabel menuKeyHeaderLabel;
  private final JLabel normalKeyHeaderLabel;
  private JHotkeyInput northBtn;
  private JHotkeyInput southBtn;
  private JHotkeyInput eastBtn;
  private JHotkeyInput westBtn;
  private boolean preferredWidthSet = false;

  public HotkeyOptions(PreferencesFrame window) {
    super(window);
    this.setLayout(new TableLayout(1));
    menuKeyHeaderLabel = new JLabel();
    add(menuKeyHeaderLabel);
    add(new JLabel(" "));
    JPanel menuKeyPanel = new JPanel();
    JAdjustableScroll menuKeyScrollPane = new JAdjustableScroll(menuKeyPanel);
    add(menuKeyScrollPane);

    add(new JLabel(" "));
    normalKeyHeaderLabel = new JLabel();
    add(normalKeyHeaderLabel);
    add(new JLabel(" "));
    JPanel normalKeyPanel = new JPanel();
    JAdjustableScroll normalKeyScrollPane = new JAdjustableScroll(normalKeyPanel);
    add(normalKeyScrollPane);

    Field[] fields = AppPreferences.class.getDeclaredFields();
    try {
      for (var f : fields) {
        String name = f.getName();
        if (name.contains("HOTKEY_")) {
          @SuppressWarnings("unchecked")
          PrefMonitor<KeyStroke> keyStroke = (PrefMonitor<KeyStroke>) f.get(AppPreferences.class);
          hotkeys.add(keyStroke);
        }
      }
    } catch (Exception e) {
      AppPreferences.hotkeyReflectError(e);
    }

    keyInputList = new ArrayList<>();
    for (int i = 0; i < hotkeys.size(); i++) {
      keyInputList.add(new JHotkeyInput(window,""));
    }


    menuKeyPanel.setMaximumSize(new Dimension(400, 400));
    menuKeyPanel.setLayout(new TableLayout(2));
    normalKeyPanel.setMaximumSize(new Dimension(400, 400));
    normalKeyPanel.setLayout(new TableLayout(2));

    final JLabel[] keyLabels = new JLabel[hotkeys.size()];
    for (int i = 0; i < hotkeys.size(); i++) {
      /* I do this chore because they have a different layout */
      if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_NORTH
          || hotkeys.get(i) == AppPreferences.HOTKEY_DIR_SOUTH
          || hotkeys.get(i) == AppPreferences.HOTKEY_DIR_EAST
          || hotkeys.get(i) == AppPreferences.HOTKEY_DIR_WEST) {
        if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_NORTH) {
          northBtn = new JHotkeyInput(window,((PrefMonitorKeyStroke) hotkeys.get(i)).getDisplayString());
          keyInputList.set(i, northBtn);
        }
        if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_SOUTH) {
          southBtn = new JHotkeyInput(window,((PrefMonitorKeyStroke) hotkeys.get(i)).getDisplayString());
          keyInputList.set(i, southBtn);
        }
        if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_EAST) {
          eastBtn = new JHotkeyInput(window,((PrefMonitorKeyStroke) hotkeys.get(i)).getDisplayString());
          keyInputList.set(i, eastBtn);
        }
        if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_WEST) {
          westBtn = new JHotkeyInput(window,((PrefMonitorKeyStroke) hotkeys.get(i)).getDisplayString());
          keyInputList.set(i, westBtn);
        }
//        keyInputList.get(i).addActionListener(listener);
//        keyInputList.get(i).setActionCommand(i + "");
//        keyInputList.get(i).setEnabled(((PrefMonitorKeyStroke) hotkeys.get(i)).canModify());
        continue;
      }
      PrefMonitorKeyStroke prefKeyStroke = ((PrefMonitorKeyStroke) hotkeys.get(i));
      keyLabels[i] = new JLabel(S.get(prefKeyStroke.getName()) + "  ");
      keyInputList.set(i, new JHotkeyInput(window,prefKeyStroke.getDisplayString()));
//      keyInputList.get(i).addActionListener(listener);
//      keyInputList.get(i).setActionCommand(i + "");
//      keyInputList.get(i).setEnabled(prefKeyStroke.canModify());
      if (prefKeyStroke.needMetaKey()) {
        menuKeyPanel.add(keyLabels[i]);
        menuKeyPanel.add(keyInputList.get(i));
      } else {
        normalKeyPanel.add(keyLabels[i]);
        normalKeyPanel.add(keyInputList.get(i));
      }
    }

    /* Layout for arrow hotkeys */
    normalKeyPanel.add(new JLabel(" "));
    normalKeyPanel.add(new JLabel(" "));
    normalKeyPanel.add(new JLabel(S.get("hotkeyOptOrientDesc")));
    normalKeyPanel.add(new JLabel(" "));
    JPanel panelLeft = new JPanel();
    JPanel panelRight = new JPanel();
    panelLeft.setLayout(new TableLayout(3));
    panelRight.setLayout(new TableLayout(3));
    panelLeft.add(new JLabel(" "));
    panelLeft.add(new JLabel(" " + S.get("hotkeyDirNorth") + " "));
    panelLeft.add(new JLabel(" "));
    panelLeft.add(new JLabel(" " + S.get("hotkeyDirWest") + " "));
    panelLeft.add(new JLabel(" " + S.get("hotkeyDirSouth") + " "));
    panelLeft.add(new JLabel(" " + S.get("hotkeyDirEast") + " "));
    normalKeyPanel.add(panelLeft);
    panelRight.add(new JLabel(" "));
    panelRight.add(northBtn);
    panelRight.add(new JLabel(" "));
    panelRight.add(westBtn);
    panelRight.add(southBtn);
    panelRight.add(eastBtn);
    normalKeyPanel.add(panelRight);

    var that = this;
    /* this timer deals with the preferred width */
    new Timer(200, e -> {
      int menuWidth = menuKeyPanel.getWidth();
      int normalWidth = normalKeyPanel.getWidth();
      if (normalWidth > 0 && normalWidth < that.getWidth() * 0.8 && !preferredWidthSet) {
        menuKeyScrollPane.preferredWidth = menuWidth + 40;
        menuKeyPanel.setPreferredSize(menuKeyPanel.getSize());
        normalKeyScrollPane.preferredWidth = normalWidth + 40;
        normalKeyPanel.setPreferredSize(normalKeyPanel.getSize());
        preferredWidthSet = true;
      }
    }).start();

    JButton resetBtn = new JButton(S.get("hotkeyOptResetBtn"));
    resetBtn.addActionListener(e -> AppPreferences.resetHotkeys());
    add(new JLabel(" "));
    add(resetBtn);
    AppPreferences.getPrefs().addPreferenceChangeListener(evt -> {
      AppPreferences.hotkeySync();
      for (int i = 0; i < hotkeys.size(); i++) {
        keyInputList.get(i).setText(((PrefMonitorKeyStroke) hotkeys.get(i)).getDisplayString());
      }
    });
  }

  @Override
  public String getHelpText() {
    return S.get("hotkeyOptHelp");
  }

  @Override
  public String getTitle() {
    return S.get("hotkeyOptTitle");
  }

  @Override
  public void localeChanged() {
    /* TODO: localize */
    menuKeyHeaderLabel.setText(S.get("hotkeyOptMenuKeyHeader",
        InputEvent.getModifiersExText(AppPreferences.hotkeyMenuMask)));
    normalKeyHeaderLabel.setText(S.get("hotkeyOptNormalKeyHeader",
        InputEvent.getModifiersExText(AppPreferences.hotkeyMenuMask)));
  }

  private class SettingsChangeListener implements ChangeListener, ActionListener {
    HotkeyOptions owner;
    private int code;
    private int modifier;

    public SettingsChangeListener(HotkeyOptions ht) {
      owner = ht;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int index = Integer.parseInt(e.getActionCommand());
      JDialog dl = new JDialog(
          owner.getPreferencesFrame(),
          S.get(((PrefMonitorKeyStroke) hotkeys.get(index)).getName()),
          true);

      JButton ok = new JButton("OK");
      JButton cancel = new JButton("Cancel");

      ok.setFocusable(false);
      ok.addActionListener(e1 -> {
        if (code != 0) {
          HotkeyOptions.hotkeys.get(index).set(KeyStroke.getKeyStroke(code, modifier));
          try {
            AppPreferences.getPrefs().flush();
            owner.keyInputList.get(index).setText(
                ((PrefMonitorKeyStroke) HotkeyOptions.hotkeys.get(index)).getDisplayString());
            AppPreferences.hotkeySync();
          } catch (BackingStoreException ex) {
            throw new RuntimeException(ex);
          }
        }
        dl.setVisible(false);
      });
      cancel.setFocusable(false);
      cancel.addActionListener(ev -> dl.setVisible(false));

      JPanel sub = new JPanel();
      sub.setLayout(new TableLayout(2));
      JPanel contentPanel = new JPanel();
      contentPanel.setLayout(new TableLayout(1));

      sub.add(ok);
      sub.add(cancel);

      JPanel top = new JPanel();
      top.setLayout(new TableLayout(3));
      JLabel waitingLabel = new JLabel("Receiving Your Input Key");
      top.add(new JLabel("  "));
      top.add(waitingLabel);
      top.add(new JLabel("  "));
      contentPanel.add(top);
      contentPanel.add(sub);

      dl.addKeyListener(new KeyCaptureListener(waitingLabel, this,
          HotkeyOptions.hotkeys.get(index)));
      dl.setContentPane(contentPanel);
      dl.setLocationRelativeTo(null);
      dl.setSize(500, 100);
      dl.setVisible(true);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      /* not-used */
    }
  }

  private class KeyCaptureListener implements KeyListener {
    private final JLabel label;
    private final SettingsChangeListener scl;
    private PrefMonitor<KeyStroke> keyStrokePrefMonitor;

    public KeyCaptureListener(JLabel l, SettingsChangeListener se,
                              PrefMonitor<KeyStroke> prefMonitor) {
      label = l;
      scl = se;
      keyStrokePrefMonitor = prefMonitor;
    }

    @Override
    public void keyTyped(KeyEvent e) {
      /* not-used */
    }

    @Override
    public void keyPressed(KeyEvent e) {
      int modifier = e.getModifiersEx();
      int code = e.getKeyCode();
      if (code == 0
          || code == KeyEvent.VK_CONTROL
          || code == KeyEvent.VK_ALT
          || code == KeyEvent.VK_SHIFT
          || code == KeyEvent.VK_META) {
        return;
      }
      if (!((PrefMonitorKeyStroke) keyStrokePrefMonitor).metaCheckPass(modifier)) {
        label.setText(S.get("hotkeyErrMeta", InputEvent.getModifiersExText(
            AppPreferences.hotkeyMenuMask)));
        scl.code = 0;
        scl.modifier = 0;
        return;
      }
      String checkPass = AppPreferences.hotkeyCheckConflict(code, modifier);
      if (!checkPass.isEmpty()) {
        label.setText(checkPass);
        scl.code = 0;
        scl.modifier = 0;
        return;
      }
      scl.code = code;
      scl.modifier = modifier;
      String modifierString = InputEvent.getModifiersExText(modifier);
      if (modifierString.isEmpty()) {
        label.setText(KeyEvent.getKeyText(code));
      } else {
        label.setText(InputEvent.getModifiersExText(modifier) + "+" + KeyEvent.getKeyText(code));
      }
    }

    @Override
    public void keyReleased(KeyEvent e) {
      /* not-used */
    }
  }
}
