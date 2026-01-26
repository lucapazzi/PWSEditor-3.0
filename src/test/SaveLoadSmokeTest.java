package test;

import pws.PWSStateMachine;
import machinery.StateMachine;
import serializer.BinaryModelSerializer;

public class SaveLoadSmokeTest {
    public static void main(String[] args) throws Exception {
        PWSStateMachine p = new PWSStateMachine("TestSmoke");
        StateMachine m = new StateMachine("M1");
        p.getAssembly().addStateMachine("m1", m);

        String filename = "test_pws.bin";
        System.out.println("Saving to: " + filename);
        BinaryModelSerializer.saveModelAndLibrary(p, p.getAssembly().getMachineLibrary(), filename);

        System.out.println("Loading from: " + filename);
        Object[] pair = BinaryModelSerializer.loadModelAndLibrary(filename);
        System.out.println("Loaded model class: " + (pair[0] != null ? pair[0].getClass().getName() : "null"));
        System.out.println("Loaded library class: " + (pair[1] != null ? pair[1].getClass().getName() : "null"));
    }
}
