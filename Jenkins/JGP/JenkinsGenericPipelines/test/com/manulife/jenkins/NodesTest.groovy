package com.manulife.jenkins

import org.testng.annotations.Test
import org.testng.Assert

class NodesTest {
    @Test
    void test_isValidNodeLabel_Valid() {
        Assert.assertTrue(Nodes.isValidNodeLabel('windows'));
    }

    @Test
    void test_isValidNodeLabel_Invalid() {
        Assert.assertFalse(Nodes.isValidNodeLabel('invalid'));
    }

    @Test
    void test_getNodeLabels() {
        Assert.assertTrue(Nodes.getNodeLabels().contains('linux, '));
        Assert.assertTrue(Nodes.getNodeLabels().contains('windows'));
    }
}