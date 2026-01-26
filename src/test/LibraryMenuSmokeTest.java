package test;

import pws.PWSStateMachine;
import machinery.StateMachine;
import pws.editor.PWSEditor;
import editor.StateMachineEditor;
import serializer.JsonModelSerializer;
import assembly.MachineLibrary;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class LibraryMenuSmokeTest {
    public static void main(String[] args) throws Exception {
        System.out.println("LibraryMenuSmokeTest: start");

        // Create a PWSStateMachine and add a StateMachine to its library
        PWSStateMachine pws = new PWSStateMachine("SMoke");
        StateMachine sm = new StateMachine("M1");
        String key = pws.getAssembly().getMachineLibrary().addMachine(sm);
        System.out.println("Added machine key=" + key + " name=" + sm.getName());

        // Save the MachineLibrary to a temporary file
        File tmp = new File("test_lib.mlib");
        JsonModelSerializer.saveMachineLibrary(pws.getAssembly().getMachineLibrary(), tmp);
        System.out.println("Saved MachineLibrary to " + tmp.getAbsolutePath());

        // Load it back
        MachineLibrary loaded = JsonModelSerializer.loadMachineLibrary(tmp);
        System.out.println("Loaded object class: " + (loaded != null ? loaded.getClass().getName() : "null"));

        // Now create the PWSEditor (in EDT) and simulate embedding and closing the editor
        final PWSEditor[] ref = new PWSEditor[1];
        SwingUtilities.invokeAndWait(() -> {
            ref[0] = new PWSEditor(pws);
            ref[0].setSize(800, 600);
            ref[0].setVisible(true);
        });

        PWSEditor editor = ref[0];

        // Set embeddedEditor via reflection
        java.lang.reflect.Field embeddedField = PWSEditor.class.getDeclaredField("embeddedEditor");
        embeddedField.setAccessible(true);
        StateMachineEditor sme = new StateMachineEditor(sm, pws.getAssembly(), "test");
        embeddedField.set(editor, sme);
        System.out.println("Programmatically set embeddedEditor.");

        // Simulate Close Editor: clear the embeddedEditor and replace the container contents
        embeddedField.set(editor, null);
        java.lang.reflect.Field contField = PWSEditor.class.getDeclaredField("machineEditorContainer");
        contField.setAccessible(true);
        JPanel container = (JPanel) contField.get(editor);
        SwingUtilities.invokeAndWait(() -> {
            container.removeAll();
            container.add(new JLabel("(closed)", SwingConstants.CENTER), BorderLayout.CENTER);
            container.revalidate();
            container.repaint();
        });
        System.out.println("Simulated Close Editor.");

        // Dispose the editor after a short pause so the GUI briefly appears during the smoke test
        Thread.sleep(800);
        SwingUtilities.invokeLater(() -> editor.dispose());

        System.out.println("LibraryMenuSmokeTest: end");
    }
}
