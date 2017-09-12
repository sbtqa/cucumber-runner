package ru.sbtqa.tag.allure;

import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;
import ru.sbtqa.tag.cucumber.TagCucumber;
import ru.yandex.qatools.allure.cucumberjvm.AllureReporter;
import ru.yandex.qatools.allure.exceptions.AllureException;

/**
 * Allure reporting plugin for cucumber-jvm
 */
public class TagAllureReporter extends AllureReporter {

    @Override
    public String getStepName(Step step) {
        return step.getName().split(TagCucumber.SECRET_DELIMITER).length > 1
                ? step.getKeyword() + step.getName().split(TagCucumber.SECRET_DELIMITER)[1]
                : step.getKeyword() + step.getName();

    }

    @Override
    public void result(Result result) {
        try {
            super.result(result);
        } catch (RuntimeException ex){
            throw new AllureException("Could not initialize callback", ex);
        }
    }
}
