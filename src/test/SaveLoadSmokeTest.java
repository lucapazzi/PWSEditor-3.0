package test;

import pws.PWSStateMachine;
import machinery.StateMachine;
import serializer.JsonModelSerializer;
import java.io.File;

public class SaveLoadSmokeTest {
    public static void main(String[] args) throws Exception {
        PWSStateMachine p = new PWSStateMachine("TestSmoke");
        StateMachine m = new StateMachine("M1");
        p.getAssembly().addStateMachine("m1", m);

        String filename = "test_pws.pws";
        System.out.println("Saving to: " + filename);
        JsonModelSerializer.savePwsWorkspace(p, null, new File(filename));

        System.out.println("Loading from: " + filename);
        JsonModelSerializer.LoadedWorkspace loaded = JsonModelSerializer.loadPwsWorkspace(new File(filename));
        System.out.println("Loaded model class: " + (loaded.getModel() != null ? loaded.getModel().getClass().getName() : "null"));
        System.out.println("Loaded library class: " + (loaded.getModel() != null ? loaded.getModel().getAssembly().getMachineLibrary().getClass().getName() : "null"));
    }
}
