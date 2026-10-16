/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.designrulecheck;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Splitter;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.StdAttr;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class SimpleDrcContainer {

  public static final int LEVEL_NORMAL = 1;
  public static final int LEVEL_SEVERE = 2;
  public static final int LEVEL_FATAL = 3;
  public static final int MARK_NONE = 0;
  public static final int MARK_INSTANCE = 1;
  public static final int MARK_LABEL = 2;
  public static final int MARK_WIRE = 4;

  private final String message;
  private final int severityLevel;
  private Set<Object> drcComponents;
  private Circuit myCircuit;
  private final int markType;
  private int listNumber;
  private final boolean suppressCount;

  public SimpleDrcContainer(String message, int level) {
    this.message = message;
    this.severityLevel = level;
    this.markType = MARK_NONE;
    this.listNumber = 0;
    this.suppressCount = false;
  }

  public SimpleDrcContainer(String message, int level, boolean suppressCount) {
    this.message = message;
    this.severityLevel = level;
    this.markType = MARK_NONE;
    this.listNumber = 0;
    this.suppressCount = suppressCount;
  }

  public SimpleDrcContainer(Object message, int level) {
    this.message = message.toString();
    this.severityLevel = level;
    this.markType = MARK_NONE;
    this.listNumber = 0;
    this.suppressCount = false;
  }

  public SimpleDrcContainer(Object message, int level, boolean suppressCount) {
    this.message = message.toString();
    this.severityLevel = level;
    this.markType = MARK_NONE;
    this.listNumber = 0;
    this.suppressCount = suppressCount;
  }

  public SimpleDrcContainer(Circuit circ, Object message, int level, int markMask) {
    this.message = message.toString();
    this.severityLevel = level;
    this.myCircuit = circ;
    this.markType = markMask;
    this.listNumber = 0;
    this.suppressCount = false;
  }

  public SimpleDrcContainer(Circuit circ, Object message, int level, int markMask, boolean suppressCount) {
    this.message = message.toString();
    this.severityLevel = level;
    this.myCircuit = circ;
    this.markType = markMask;
    this.listNumber = 0;
    this.suppressCount = suppressCount;
  }

  private static final int MAX_LISTED_COMPONENTS = 5;

  @Override
  public String toString() {
    final var marked = describeMarkedComponents();
    return marked.isEmpty() ? message : message + " " + marked;
  }

  /** The components a message is about, e.g. "[Controlled Buffer (240,310), RAM "memory" (400,120)]". */
  private String describeMarkedComponents() {
    if (!isDrcInfoPresent()) return "";
    final var names = new TreeSet<String>();
    for (final var obj : drcComponents) {
      if (obj instanceof Wire wire) {
        names.add(wire.getEnd0() + "-" + wire.getEnd1());
      } else if (obj instanceof Component comp) {
        final var attrs = comp.getAttributeSet();
        final var label = attrs.containsAttribute(StdAttr.LABEL) ? attrs.getValue(StdAttr.LABEL) : null;
        names.add(comp.getFactory().getDisplayName()
            + (label == null || label.isEmpty() ? "" : " \"" + label + "\"") + " " + comp.getLocation());
      }
    }
    if (names.isEmpty()) return "";
    final var listed = new StringBuilder();
    var count = 0;
    for (final var name : names) {
      if (count++ == MAX_LISTED_COMPONENTS) {
        listed.append(", +").append(names.size() - MAX_LISTED_COMPONENTS);
        break;
      }
      if (listed.length() > 0) listed.append(", ");
      listed.append(name);
    }
    return "[" + listed + "]";
  }

  public int getSeverity() {
    return severityLevel;
  }

  public boolean isDrcInfoPresent() {
    if (drcComponents == null || myCircuit == null) return false;
    return !drcComponents.isEmpty();
  }

  public Circuit getCircuit() {
    return myCircuit;
  }

  public boolean hasCircuit() {
    return (myCircuit != null);
  }

  public void addMarkComponent(Object comp) {
    if (drcComponents == null) drcComponents = new HashSet<>();
    drcComponents.add(comp);
  }

  public void addMarkComponents(Set<?> set) {
    if (drcComponents == null) drcComponents = new HashSet<>();
    drcComponents.addAll(set);
  }

  public void setListNumber(int number) {
    listNumber = number;
  }

  public boolean getSuppressCount() {
    return suppressCount;
  }

  public int getListNumber() {
    return listNumber;
  }

  public void markComponents() {
    if (!isDrcInfoPresent()) return;
    for (final var obj : drcComponents) {
      if (obj instanceof Wire wire) {
        if ((markType & MARK_WIRE) != 0) {
          wire.setDrcHighlight(true);
        }
      } else if (obj instanceof Splitter split) {
        if ((markType & MARK_INSTANCE) != 0) {
          split.setMarked(true);
        }
      } else if (obj instanceof InstanceComponent comp) {
        if ((markType & MARK_INSTANCE) != 0) comp.markInstance();
        if ((markType & MARK_LABEL) != 0) comp.markLabel();
      } else {
      }
    }
  }

  public void clearMarks() {
    if (!isDrcInfoPresent()) return;
    for (final var obj : drcComponents) {
      if (obj instanceof Wire wire) {
        if ((markType & MARK_WIRE) != 0) {
          wire.setDrcHighlight(false);
        }
      } else if (obj instanceof Splitter split) {
        if ((markType & MARK_INSTANCE) != 0) {
          split.setMarked(false);
        }
      } else if (obj instanceof InstanceComponent comp) {
        comp.clearMarks();
      }
    }
  }
}
