package pws.editor;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Non-modal window showing LTL check results. */
@SuppressWarnings("this-escape")
public class LTLChecksDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final DefaultListModel<LTLCheckResult> resultsModel = new DefaultListModel<>();
    private final JList<LTLCheckResult> resultsList = new JList<>(resultsModel);
    private final JLabel summaryLabel = new JLabel("No checks run yet.");
    private transient Runnable onOpenEditor;
    private transient Runnable onRecheck;

    public LTLChecksDialog(Window owner) {
        super(owner, "LTL Checks", ModalityType.MODELESS);
        setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
        add(summaryLabel, BorderLayout.NORTH);

        resultsList.setVisibleRowCount(10);
        JScrollPane scrollPane = new JScrollPane(resultsList);
        scrollPane.setPreferredSize(new Dimension(720, 260));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton openEditorBtn = new JButton("Open LTL Editor");
        JButton recheckBtn = new JButton("Recheck");
        JButton closeBtn = new JButton("Close");

        openEditorBtn.addActionListener(e -> {
            if (onOpenEditor != null) onOpenEditor.run();
        });
        recheckBtn.addActionListener(e -> {
            if (onRecheck != null) onRecheck.run();
        });
        closeBtn.addActionListener(e -> setVisible(false));

        buttons.add(openEditorBtn);
        buttons.add(recheckBtn);
        buttons.add(closeBtn);
        add(buttons, BorderLayout.SOUTH);

        pack();
    }

    public void setResults(List<LTLCheckResult> results) {
        resultsModel.clear();
        if (results == null || results.isEmpty()) {
            summaryLabel.setText("No LTL formulas to check.");
            return;
        }

        int pass = 0, fail = 0, skipped = 0, error = 0;
        for (LTLCheckResult r : results) {
            resultsModel.addElement(r);
            switch (r.getStatus()) {
                case PASS:
                    pass++;
                    break;
                case FAIL:
                    fail++;
                    break;
                case SKIPPED:
                    skipped++;
                    break;
                case ERROR:
                    error++;
                    break;
                default:
                    break;
            }
        }
        summaryLabel.setText("Pass: " + pass + "  Fail: " + fail + "  Skipped: " + skipped + "  Error: " + error);
        resultsList.setSelectedIndex(resultsModel.isEmpty() ? -1 : 0);
    }

    public void setOnOpenEditor(Runnable onOpenEditor) {
        this.onOpenEditor = onOpenEditor;
    }

    public void setOnRecheck(Runnable onRecheck) {
        this.onRecheck = onRecheck;
    }
}
