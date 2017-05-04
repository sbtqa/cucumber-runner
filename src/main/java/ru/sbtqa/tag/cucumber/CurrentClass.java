package ru.sbtqa.tag.cucumber;

import org.junit.runners.model.TestClass;

class CurrentClass {
    private TestClass testClass;
    private boolean isTranslated = false;

    public CurrentClass(TestClass testClass) {
        this.testClass = testClass;
    }

    public CurrentClass markTranslated() {
        this.isTranslated = true;
        return this;
    }

    public boolean isTranslated(TestClass testClass) {
        return testClass.equals(this.testClass) && isTranslated;
    }

    public TestClass getTestClass() {
        return this.testClass;
    }
}
