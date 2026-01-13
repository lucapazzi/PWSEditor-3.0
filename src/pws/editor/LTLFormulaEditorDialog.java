package pws.editor;

import assembly.Assembly;
import assembly.AssemblyInterface;
import assembly.LTLFormula;
import smalgebra.BasicStateProposition;
import assembly.LTLValidator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/** Dialog for creating, validating, and editing LTL formulas. */
public class LTLFormulaEditorDialog extends JDialog {
    private final AssemblyInterface assembly;
    private DefaultListModel<LTLFormula> formulasModel = new DefaultListModel<>();
    private JList<LTLFormula> formulasList;
    private JTextArea formulaArea;
    private JList<BasicStateProposition> alphabetList;
    private JComboBox<String> kindCombo;

    /**
     * Creates a dialog for editing LTL formulas.
     *
     * @param owner dialog owner
     * @param assembly assembly context
     */
    public LTLFormulaEditorDialog(Window owner, AssemblyInterface assembly) {
        super(owner, "LTL Formula Editor", ModalityType.APPLICATION_MODAL);
        this.assembly = assembly;
        initUI();
        loadFormulas();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(8,8));

        // Left: list of formulas
        formulasList = new JList<>(formulasModel);
        formulasList.setVisibleRowCount(10);
        JScrollPane listScroll = new JScrollPane(formulasList);
        listScroll.setPreferredSize(new Dimension(320, 180));

        // Right: editor panel
        JPanel editor = new JPanel(new BorderLayout(4,4));
        formulaArea = new JTextArea(8, 40);
        JScrollPane textScroll = new JScrollPane(formulaArea);

        // alphabet selector + insert buttons (multi-select)
        JPanel insertPanel = new JPanel(new BorderLayout());
        List<BasicStateProposition> guards = assembly.getAssemblyGuards();
        alphabetList = new JList<>(guards.toArray(new BasicStateProposition[0]));
        alphabetList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane alphabetScroll = new JScrollPane(alphabetList);
        alphabetScroll.setPreferredSize(new Dimension(220, 80));

        JPanel insertButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton insertProp = new JButton("Insert");
        insertProp.addActionListener((ActionEvent e) -> {
            BasicStateProposition p = alphabetList.getSelectedValue();
            if (p != null) formulaArea.insert(p.toString(), formulaArea.getCaretPosition());
        });
        JButton insertOr = new JButton("Insert OR");
        insertOr.addActionListener(a -> {
            List<BasicStateProposition> sel = alphabetList.getSelectedValuesList();
            if (!sel.isEmpty()) {
                StringBuilder sb = new StringBuilder("(");
                for (int i = 0; i < sel.size(); i++) {
                    if (i > 0) sb.append(" | ");
                    sb.append(sel.get(i).toString());
                }
                sb.append(")");
                formulaArea.insert(sb.toString(), formulaArea.getCaretPosition());
            }
        });
        JButton insertAnd = new JButton("Insert AND");
        insertAnd.addActionListener(a -> {
            List<BasicStateProposition> sel = alphabetList.getSelectedValuesList();
            if (!sel.isEmpty()) {
                StringBuilder sb = new StringBuilder("(");
                for (int i = 0; i < sel.size(); i++) {
                    if (i > 0) sb.append(" & ");
                    sb.append(sel.get(i).toString());
                }
                sb.append(")");
                formulaArea.insert(sb.toString(), formulaArea.getCaretPosition());
            }
        });

        insertButtons.add(insertProp); insertButtons.add(insertOr); insertButtons.add(insertAnd);

        // Kind selector
        kindCombo = new JComboBox<>(new String[]{"safety", "liveness", "other"});
        JPanel kindPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        kindPanel.add(new JLabel("Kind:"));
        kindPanel.add(kindCombo);

        insertPanel.add(alphabetScroll, BorderLayout.NORTH);
        insertPanel.add(insertButtons, BorderLayout.CENTER);
        insertPanel.add(kindPanel, BorderLayout.SOUTH);

        // Templates
        JPanel templates = new JPanel(new GridLayout(2,2,4,4));
        templates.setBorder(BorderFactory.createTitledBorder("Templates"));
        JButton t1 = new JButton("Safety: G (!{p})");
        t1.addActionListener(a -> insertTemplate("G (!{p})"));
        JButton t2 = new JButton("Safety: G ({p} -> X {q})");
        t2.addActionListener(a -> insertTemplate("G ({p} -> X {q})"));
        JButton t3 = new JButton("Liveness: F ({p})");
        t3.addActionListener(a -> insertTemplate("F ({p})"));
        JButton t4 = new JButton("Liveness: G ({p} -> F {q})");
        t4.addActionListener(a -> insertTemplate("G ({p} -> F {q})"));
        JButton t5 = new JButton("Response: G ({p} -> F {q})");
        t5.addActionListener(a -> insertTemplate("G ({p} -> F {q})"));
        JButton t6 = new JButton("Until: G ({p} -> ( !{q} U {r} ))");
        t6.addActionListener(a -> insertTemplate("G ({p} -> ( !{q} U {r} ))"));
        templates.add(t1); templates.add(t2); templates.add(t3); templates.add(t4);
        templates.add(t5); templates.add(t6);

        JPanel northRight = new JPanel(new BorderLayout());
        northRight.add(insertPanel, BorderLayout.NORTH);
        northRight.add(templates, BorderLayout.CENTER);

        editor.add(northRight, BorderLayout.NORTH);
        editor.add(textScroll, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton newBtn = new JButton("New");
        JButton saveBtn = new JButton("Save");
        JButton deleteBtn = new JButton("Delete");
        JButton closeBtn = new JButton("Close");

        newBtn.addActionListener(a -> createNewFormula());
        saveBtn.addActionListener(a -> saveSelectedFormula());
        deleteBtn.addActionListener(a -> deleteSelectedFormula());
        closeBtn.addActionListener(a -> setVisible(false));

        buttons.add(newBtn); buttons.add(saveBtn); buttons.add(deleteBtn); buttons.add(closeBtn);
        editor.add(buttons, BorderLayout.SOUTH);

        add(listScroll, BorderLayout.WEST);
        add(editor, BorderLayout.CENTER);

        formulasList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                LTLFormula f = formulasList.getSelectedValue();
                if (f != null) {
                    formulaArea.setText(f.getFormulaText());
                }
            }
        });
    }

    private void insertTemplate(String tmpl) {
        formulaArea.insert(tmpl, formulaArea.getCaretPosition());
    }

    private void loadFormulas() {
        formulasModel.clear();
        try {
            for (LTLFormula f : assembly.getLTLFormulas()) {
                formulasModel.addElement(f);
            }
        } catch (Exception ex) {
            // Assembly may not implement storage; ignore
        }
    }

    private void createNewFormula() {
        String newId = "f" + (formulasModel.getSize() + 1);
        LTLFormula f = new LTLFormula(newId, "", "safety");
        formulasModel.addElement(f);
        formulasList.setSelectedValue(f, true);
    }

    private void saveSelectedFormula() {
        LTLFormula f = formulasList.getSelectedValue();
        if (f == null) {
            JOptionPane.showMessageDialog(this, "Select a formula to save.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String text = formulaArea.getText().trim();
        // Validate formula before saving (syntax + alphabet)
        String err = LTLValidator.validate(text, assembly);
        if (err != null) {
            JOptionPane.showMessageDialog(this, "Invalid formula: " + err, "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Parse and classify semantically
        try {
            // Use reflection so the editor compiles even if LTL parser/analyzer are absent.
            Object node = null;
            try {
                Class<?> parserClass = Class.forName("pws.editor.LTLParser");
                java.lang.reflect.Method parseMethod = parserClass.getMethod("parse", String.class);
                node = parseMethod.invoke(null, text);
            } catch (ClassNotFoundException cnf) {
                // Parser not present; accept formula text but skip classification
            }

            String kindName = null;
            if (node != null) {
                try {
                    Class<?> analyzerClass = Class.forName("assembly.LTLAnalyzer");
                    java.lang.reflect.Method classify = analyzerClass.getMethod("classify", Object.class);
                    Object kindObj = classify.invoke(null, node);
                    if (kindObj != null) kindName = kindObj.toString();
                } catch (ClassNotFoundException cnf) {
                    // analyzer not present; ignore
                }
            }

            f.setFormulaText(text);
            if (kindName != null) {
                f.setKind(kindName.toLowerCase());
                kindCombo.setSelectedItem(f.getKind());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Parse/classify error: " + ex.getMessage(), "Parse Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // if formula not yet present in assembly storage, add it
        if (!assembly.getLTLFormulas().contains(f)) {
            try { assembly.addLTLFormula(f); } catch (Exception ex) { }
        }
        formulasList.repaint();
    }

    private void deleteSelectedFormula() {
        LTLFormula f = formulasList.getSelectedValue();
        if (f == null) return;
        int confirm = JOptionPane.showConfirmDialog(this, "Delete formula " + f.getId() + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            formulasModel.removeElement(f);
            try { assembly.removeLTLFormula(f); } catch (Exception ex) { }
            formulaArea.setText("");
        }
    }
}
