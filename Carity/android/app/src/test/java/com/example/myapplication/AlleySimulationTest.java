package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class AlleySimulationTest {
    @Test public void exactThresholds() {
        assertEquals(AlleySimulation.Level.CLEAR, AlleySimulation.Level.from(.6001));
        assertEquals(AlleySimulation.Level.CAUTION, AlleySimulation.Level.from(.60));
        assertEquals(AlleySimulation.Level.CAUTION, AlleySimulation.Level.from(.3001));
        assertEquals(AlleySimulation.Level.NEAR, AlleySimulation.Level.from(.30));
    }
    @Test public void rotatedOuterBodyRatherThanCentre() {
        AlleySimulation m = new AlleySimulation();
        assertEquals(1.1,m.gap(AlleySimulation.Side.RIGHT),1e-9);
        m.heading=30;
        double extent=.9*Math.cos(Math.PI/6)+2*Math.sin(Math.PI/6);
        assertEquals(2-extent,m.gap(AlleySimulation.Side.RIGHT),1e-9);
        assertEquals(2-extent,m.gap(AlleySimulation.Side.LEFT),1e-9);
        m.x=3;
        assertEquals(0,m.gap(AlleySimulation.Side.RIGHT),0);
        m.x=-1;
        assertEquals(0,m.gap(AlleySimulation.Side.LEFT),0);
    }
    @Test public void entryIsOnceUntilExitAndReentryOnBothSides() {
        for(AlleySimulation.Side side:AlleySimulation.Side.values()) {
            AlleySimulation m=new AlleySimulation();
            assertTrue(m.pollEntries().isEmpty());
            m.x=side==AlleySimulation.Side.RIGHT ? 2.85 : 1.15;
            assertEquals(java.util.Collections.singletonList(side),m.pollEntries());
            for(int i=0;i<50;i++) assertTrue(m.pollEntries().isEmpty());
            m.x=2;
            assertTrue(m.pollEntries().isEmpty());
            m.x=side==AlleySimulation.Side.RIGHT ? 2.85 : 1.15;
            assertEquals(java.util.Collections.singletonList(side),m.pollEntries());
            m.reset(); assertTrue(m.pollEntries().isEmpty());
        }
    }
    @Test public void demoSequenceGoesGreenOrangeRedAndRearms() {
        AlleySimulation m=new AlleySimulation();
        m.steer(10);
        assertEquals(AlleySimulation.Level.CLEAR,AlleySimulation.Level.from(m.gap(AlleySimulation.Side.RIGHT)));
        for(int i=0;i<5;i++)m.forward();
        assertEquals(AlleySimulation.Level.CAUTION,AlleySimulation.Level.from(m.gap(AlleySimulation.Side.RIGHT)));
        for(int i=0;i<7;i++)m.forward();
        assertEquals(AlleySimulation.Level.NEAR,AlleySimulation.Level.from(m.gap(AlleySimulation.Side.RIGHT)));
        assertEquals(1,m.pollEntries().size());
        assertTrue(m.pollEntries().isEmpty());
        m.steer(-20);
        for(int i=0;i<8;i++)m.forward();
        assertEquals(AlleySimulation.Level.CAUTION,AlleySimulation.Level.from(m.gap(AlleySimulation.Side.RIGHT)));
        assertTrue(m.pollEntries().isEmpty());
        m.steer(20);
        for(int i=0;i<8;i++)m.forward();
        assertEquals(java.util.Collections.singletonList(AlleySimulation.Side.RIGHT),m.pollEntries());
    }
}
