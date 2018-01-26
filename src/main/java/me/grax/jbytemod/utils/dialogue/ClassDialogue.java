package me.grax.jbytemod.utils.dialogue;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.NumberFormat;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.NumberFormatter;
import javax.swing.text.PlainDocument;

import me.grax.jbytemod.JByteMod;

public class ClassDialogue {

  private Object object;
  private Class<? extends Object> clazz;
  private ArrayList<Field> fields;

  private String title;

  private static final List<String> noChilds = Arrays.asList(String.class.getName(), Integer.class.getName(), int.class.getName(),
      char.class.getName(), Character.class.getName(), boolean.class.getName(), Boolean.class.getName(), char[].class.getName());

  public ClassDialogue(Object object) {
    this(object, "Edit " + object.getClass().getSimpleName());
  }

  public ClassDialogue(Object object, String title) {
    if (object instanceof AbstractCollection<?>) {
      object = ((AbstractCollection<?>) object).toArray(); //we already have an editing table for that
    }
    this.object = object;
    this.clazz = object.getClass();
    this.title = title;
    this.initializeFields();
  }

  private void initializeFields() {
    fields = new ArrayList<>();
    for (Field f : clazz.getDeclaredFields()) {
      if (!Modifier.isStatic(f.getModifiers()) && !ignore(f.getName())) {
        f.setAccessible(true);
        fields.add(f);
      }
    }
  }

  protected boolean ignore(String name) {
    return false;
  }

  public boolean open() {
    JPanel panel = initializePanel();
    JPanel rightInput = (JPanel) panel.getComponent(1);
    if (JOptionPane.showConfirmDialog(null, panel, title, JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
      for (Component c : rightInput.getComponents()) {
        WrappedPanel wp = (WrappedPanel) c;
        Field f = wp.getField();
        if (f != null) {
          if (isModifiedSpecial(f.getName(), f.getType())) {
            Object o = getSpecialValue(object, f.getName(), f.getType(), wp.getObject(), wp);
            try {
              f.set(object, o);
            } catch (IllegalArgumentException | IllegalAccessException e) {
              e.printStackTrace();
            }
            continue;
          }
          Component child = wp.getComponent(0);
          if (hasNoChilds(f.getType())) {
            try {
              f.set(object, getValue(f.getType(), child));
            } catch (IllegalArgumentException | IllegalAccessException e) {
              e.printStackTrace();
            }
          }
        } else {
          Object o = wp.getObject();
          getSpecialValue(object, o.getClass().getName(), o.getClass(), o, wp); //can also be used as void
        }
      }
      return true;
    }
    return false;
  }

  protected Object getSpecialValue(Object object, String name, Class<?> type, Object object3, WrappedPanel wp) {
    return null;
  }

  private Object getValue(Class<?> type, Component child) {
    switch (noChilds.indexOf(type.getName())) {
    case 0:
      JTextField jtf = (JTextField) child;
      return jtf.getText();
    case 1:
    case 2:
      JFormattedTextField numberField = (JFormattedTextField) child;
      return numberField.getValue();
    case 3:
    case 4:
      JCharField jcf = (JCharField) child;
      return jcf.getCharacter();
    case 5:
    case 6:
      return ((JCheckBox) child).isSelected();
    case 7:
      jtf = (JTextField) child;
      return jtf.getText().toCharArray();
    default:
      throw new RuntimeException("" + noChilds.indexOf(type));
    }
  }

  private JPanel initializePanel() {
    JPanel mainPanel = new JPanel();
    JPanel leftText = new JPanel();
    JPanel rightInput = new JPanel();

    mainPanel.setLayout(new BorderLayout());
    leftText.setLayout(new GridLayout(0, 1));
    rightInput.setLayout(new GridLayout(0, 1));
    addSpecialInputs(object, leftText, rightInput);
    for (Field f : fields) {
      if (isModifiedSpecial(f.getName(), f.getType())) {
        try {
          rightInput.add(wrap(f, getModifiedSpecial(f.get(object), f.getName(), f.getType())));
        } catch (IllegalArgumentException | IllegalAccessException e) {
          e.printStackTrace();
        }
      } else if (hasNoChilds(f.getType())) {
        try {
          rightInput.add(wrap(f, getComponent(f)));
        } catch (IllegalArgumentException | IllegalAccessException e) {
          e.printStackTrace();
        }
      } else if (f.getType().isArray() || isList(f)) {
        JButton edit = new JButton("Edit " + f.getType().getSimpleName());
        edit.addActionListener(e -> {
          try {
            ListEditorTable t = new ListEditorTable(object, f);
            if (t.open()) {
              f.set(object, f.getType().isArray() ? t.getList().toArray() : t.getList());
            }
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        });
        rightInput.add(wrap(f, edit));
      } else {
        JButton edit = new JButton(JByteMod.res.getResource("edit"));
        Object value;
        try {
          value = f.get(object);
          edit.addActionListener(e -> {
            try {
              //should still be the same class type
              ClassDialogue dialogue = ClassDialogue.this.init(value);
              if (dialogue.open()) {
                f.set(object, dialogue.getObject());
              }
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          });
          if (value == null) {
            edit.setEnabled(false);
          }
        } catch (IllegalArgumentException | IllegalAccessException e1) {
          e1.printStackTrace();
        }
        rightInput.add(wrap(f, edit));
      }
      leftText.add(new JLabel(formatText(f.getName()) + ": "));
    }

    mainPanel.add(leftText, BorderLayout.WEST);
    mainPanel.add(rightInput, BorderLayout.CENTER);

    return mainPanel;
  }

  private boolean isList(Field f) {
    if (AbstractCollection.class.isAssignableFrom(f.getType()))
      return true;
    try {
      Object o = f.get(object);
      return o instanceof AbstractCollection;
    } catch (Throwable t) {
    }
    return false;
  }

  protected ClassDialogue init(Object value) {
    return new ClassDialogue(value);
  }

  protected void addSpecialInputs(Object object, JPanel leftText, JPanel rightInput) {
  }

  protected Component getModifiedSpecial(Object object, String name, Class<?> type) {
    return null;
  }

  protected boolean isModifiedSpecial(String name, Class<?> type) {
    return false;
  }

  protected Component wrap(Field f, Component... component) {
    WrappedPanel wp = new WrappedPanel(f);
    for (Component c : component)
      wp.add(c);
    return wp;
  }

  protected Component wrap(Object o, Component... component) {
    WrappedPanel wp = new WrappedPanel(o);
    for (Component c : component)
      wp.add(c);
    return wp;
  }

  public Object getObject() {
    return object;
  }

  @SuppressWarnings("unused")
  private Component wrapRight(Field f, Component component, Component component2) {
    WrappedPanel wp = new WrappedPanel(f);
    wp.add(component, BorderLayout.CENTER);
    wp.add(component2, BorderLayout.EAST);
    return wp;
  }

  private Component getComponent(Field f) throws IllegalArgumentException, IllegalAccessException {
    return getComponent(f.getType(), f.get(object));
  }

  protected Component getComponent(Class<?> c, Object o) throws IllegalArgumentException, IllegalAccessException {
    switch (noChilds.indexOf(c.getName())) {
    case 0:
      return new JTextField(String.valueOf(o));
    case 1:
    case 2:
      JFormattedTextField numberField = createNumberField();
      numberField.setValue(o);
      return numberField;
    case 3:
    case 4:
      return new JCharField(o);
    case 5:
    case 6:
      return new JCheckBox("", (boolean) o);
    case 7:
      return new JTextField(new String((char[]) o));
    default:
      throw new RuntimeException();
    }
  }

  private String formatText(String string) {
    if (string.length() < 3) {
      //may be obfuscated, do not uppercase
      return string;
    }
    return string.substring(0, 1).toUpperCase() + string.substring(1);
  }

  private boolean hasNoChilds(Class<?> type) {
    return noChilds.contains(type.getName());
  }

  private static NumberFormatter formatter = null;

  public static JFormattedTextField createNumberField() {
    if (formatter == null) {
      NumberFormat format = NumberFormat.getInstance();
      format.setGroupingUsed(false);
      formatter = new NumberFormatter(format);
      formatter.setValueClass(Integer.class);
      formatter.setMinimum(0);
      formatter.setMaximum(Integer.MAX_VALUE);
      formatter.setAllowsInvalid(false);
      formatter.setCommitsOnValidEdit(true);
      formatter.setOverwriteMode(true);
    }
    return new JFormattedTextField(formatter);
  }

  public static class WrappedPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private Field f;

    private Object o;

    public WrappedPanel(Field f) {
      super();
      this.f = f;
      this.setLayout(new BorderLayout());
    }

    public WrappedPanel(Object o) {
      super();
      this.o = o;
      this.setLayout(new BorderLayout());
    }

    public Field getField() {
      return f;
    }

    public Object getObject() {
      return o;
    }
  }

  public class ListEditorTable extends JDialog {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("rawtypes")
    private List list;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ListEditorTable(Object parent, Field f) throws IllegalArgumentException, IllegalAccessException {
      Object item = f.get(parent);
      if (item == null) {
        this.list = new ArrayList<>();
      } else if (item.getClass().isArray()) {
        int size = Array.getLength(item);
        ArrayList list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
          list.add(Array.get(item, i));
        }
        this.list = list;
      } else if (item instanceof List<?>) {
        this.list = (List<?>) item;
      } else {
        throw new RuntimeException();
      }
    }

    private JPanel initializePanel() {
      JPanel mainPanel = new JPanel();
      JPanel leftText = new JPanel();
      JPanel rightInput = new JPanel();

      int size = list.size();

      mainPanel.setLayout(new BorderLayout(15, 15));
      leftText.setLayout(new GridLayout(size, 1));
      rightInput.setLayout(new GridLayout(size, 1));
      mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
      DefaultTableModel lm = new DefaultTableModel();
      lm.addColumn("#");
      lm.addColumn("Item");
      lm.addColumn("toString");
      for (int i = 0; i < size; i++) {
        Object o = list.get(i);
        if (o == null) {
          lm.addRow(new Object[] { i, "null", null });
        }
        lm.addRow(new Object[] { i, o.getClass().getSimpleName(), o });
      }
      JTable jtable = new JTable() {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        };
      };
      jtable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
      jtable.getTableHeader().setReorderingAllowed(false);
      jtable.setModel(lm);
      JPanel actions = new JPanel();
      actions.setLayout(new GridLayout(1, 4));
      //TODO more list editing functions
      //      JButton add = new JButton(JByteMod.res.getResource("add"));
      //      add.addActionListener(a -> {
      //        JOptionPane.showMessageDialog(null, "Currently not supported :/");
      //        //        int c = lm.getRowCount();
      //        //        lm.addRow(new Object[] { c, list.getClass().getSimpleName(),  });
      //        //        jtable.setRowSelectionInterval(c, c);
      //      });
      //      actions.add(add);
      //      JButton addNull = new JButton("Add null");
      //      addNull.addActionListener(a -> {
      //        JOptionPane.showMessageDialog(null, "Currently not supported :/");
      //        //        int c = lm.getRowCount();
      //        //        lm.addRow(new Object[] { c, list.getClass().getSimpleName(),  });
      //        //        jtable.setRowSelectionInterval(c, c);
      //      });
      //      actions.add(addNull);
      JButton remove = new JButton(JByteMod.res.getResource("remove"));
      remove.addActionListener(a -> {
        int[] selectedRows = jtable.getSelectedRows();
        if (selectedRows.length > 0) {
          for (int j = selectedRows.length - 1; j >= 0; j--) {
            lm.removeRow(selectedRows[j]);
          }
        }
      });
      actions.add(remove);
      JButton edit = new JButton(JByteMod.res.getResource(JByteMod.res.getResource("edit")));
      edit.addActionListener(a -> {
        int row = jtable.getSelectedRow();
        if(row == -1) {
          return;
        }
        Object o = lm.getValueAt(row, 2);
        if (o != null) {
          extraEditWindow(o, row, jtable);
        } else {
          JOptionPane.showMessageDialog(null, "null cannot be edited!");
        }
      });
      actions.add(edit);

      JScrollPane jscp = new JScrollPane(jtable) {
        @Override
        public Dimension getPreferredSize() {
          int maxWidth = 500;
          int maxHeight = 300;
          Dimension dim = super.getPreferredSize();
          if (dim.width > maxWidth)
            dim.width = maxWidth;
          if (dim.height > maxHeight)
            dim.height = maxHeight;
          return dim;
        }
      };
      mainPanel.add(jscp, BorderLayout.CENTER);
      mainPanel.add(actions, BorderLayout.PAGE_END);
      return mainPanel;
    }

    @SuppressWarnings("unchecked")
    public boolean open() {
      JPanel panel = initializePanel();
      JScrollPane scrollPane = (JScrollPane) panel.getComponent(0);
      JTable table = (JTable) scrollPane.getViewport().getView();
      if (JOptionPane.showConfirmDialog(null, panel, "Edit Array", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
        for(int row = 0; row < table.getRowCount(); row++) {
          int i = (int) table.getValueAt(row, 0);
          Object o = table.getValueAt(row, 2);
          list.set(i, o);
        }
        return true;
      }
      return false;
    }

    public List<?> getList() {
      return list;
    }

    public void extraEditWindow(Object o, int row, JTable jtable) {
      JPanel mainPanel = new JPanel();
      JPanel leftText = new JPanel();
      JPanel rightInput = new JPanel();

      int size = list.size();

      mainPanel.setLayout(new BorderLayout(15, 15));
      leftText.setLayout(new GridLayout(size, 1));
      rightInput.setLayout(new GridLayout(size, 1));
      if (isModifiedSpecial(o.getClass().getName(), o.getClass())) {
        rightInput.add(wrap(o, getModifiedSpecial(o, o.getClass().getName(), o.getClass())));
      } else if (hasNoChilds(o.getClass())) {
        try {
          rightInput.add(wrap(o, ClassDialogue.this.getComponent(o.getClass(), o)));
        } catch (IllegalArgumentException | IllegalAccessException e) {
          e.printStackTrace();
        }
      } else {
        JButton edit = new JButton(JByteMod.res.getResource("edit"));
        edit.addActionListener(e -> {
          try {
            ClassDialogue dialogue = ClassDialogue.this.init(o);
            dialogue.open();
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        });
        rightInput.add(wrap(o, edit));
      }
      mainPanel.add(leftText, BorderLayout.WEST);
      mainPanel.add(rightInput, BorderLayout.CENTER);
      leftText.add(new JLabel(formatText(o.getClass().getSimpleName() + ":")));
      Object newObject = null;
      if (JOptionPane.showConfirmDialog(null, mainPanel, "Edit List Item", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
        for (Component c : rightInput.getComponents()) {
          WrappedPanel wp = (WrappedPanel) c;
          if (o != null) {
            Component child = wp.getComponent(0);
            if (isModifiedSpecial(o.getClass().getName(), o.getClass())) {
              newObject = getSpecialValue(object, o.getClass().getName(), o.getClass(), o, wp);
            } else if (hasNoChilds(o.getClass())) {
              newObject = getValue(o.getClass(), child);
            }
          }
        }
        if (newObject == null) {
          newObject = o;
        }
        DefaultTableModel lm = (DefaultTableModel) jtable.getModel();
        lm.insertRow(row, new Object[] { row, newObject.getClass().getSimpleName(), newObject });
        lm.removeRow(row);
      }
    }
  }

  class ValueRenderer implements TableCellRenderer {

    private Component component;

    public ValueRenderer(Component c) {
      this.component = c;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      component.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      return component;
    }
  }

  class TableEditor extends AbstractCellEditor implements TableCellEditor {
    private Object o;
    private Component c;

    public TableEditor(Component c) {
      this.c = c;
    }

    @Override
    public Object getCellEditorValue() {
      return o;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      o = value;
      return c;
    }
  }

  public class JCharField extends JTextField {
    private static final long serialVersionUID = 1L;
    private Object c;

    public JCharField(Object c) {
      this.c = c;
      this.setText(String.valueOf(c));

    }

    public char getCharacter() {
      if (this.getText().isEmpty()) {
        return String.valueOf(c).charAt(0);
      }
      return this.getText().charAt(0);
    }

    protected Document createDefaultModel() {
      return new LimitDocument();
    }

    private class LimitDocument extends PlainDocument {
      private static final long serialVersionUID = 1L;

      @Override
      public void insertString(int offset, String str, AttributeSet attr) throws BadLocationException {
        if (str == null)
          return;

        if ((getLength() + str.length()) <= 1) {
          super.insertString(offset, str, attr);
        }
      }

    }

  }

}