package ru.sbtqa.tag.cucumber;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import cucumber.runtime.CucumberException;
import cucumber.runtime.JdkPatternArgumentMatcher;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.StepDefinition;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.StepContainer;
import gherkin.formatter.model.Step;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.slf4j.LoggerFactory;
import ru.sbtqa.tag.qautils.i18n.I18N;
import ru.sbtqa.tag.qautils.i18n.I18NRuntimeException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * <p>
 * Classes annotated with {@code @RunWith(TagCucumber.class)} will run a
 * Cucumber Feature with I18N support. The class should be empty without any
 * fields or methods.
 * </p>
 * <p>
 * Cucumber will look for a {@code .feature} file on the classpath, using the
 * same resource path as the annotated class ({@code .class} substituted by
 * {@code .feature}).
 * </p>
 * Additional hints can be given to Cucumber by annotating the class with
 * {@link CucumberOptions}.
 *
 * @see CucumberOptions
 */
public class TagCucumber extends Cucumber {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(TagCucumber.class);
    static final String SECRET_DELIMITER = ":::==!!SECRET!!==:::";
    private static Map<String, StepDefinition> stepDefinitionsByPattern;
    public static CucumberFeature cucumberFeature;

    /**
     * Constructor called by JUnit.
     *
     * @param clazz the class with the @RunWith annotation.
     * @throws java.io.IOException if there is a problem
     * @throws org.junit.runners.model.InitializationError if there is another
     * problem
     * @throws java.lang.IllegalAccessException if any reflection error
     */
    @SuppressWarnings("unchecked")
    public TagCucumber(Class clazz) throws InitializationError, IOException, IllegalAccessException {
        super(clazz);
//
//        Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
//
//        RuntimeOptions options = (RuntimeOptions) FieldUtils.readField(runtime, "runtimeOptions", true);
//        List pluginFormatterNames = (List) FieldUtils.readField(options, "pluginFormatterNames", true);
//
//        pluginFormatterNames.remove("ru.yandex.qatools.allure.cucumberjvm.AllureReporter");
//        options.addPlugin(new TagAllureReporter());
//
//        FieldUtils.writeField(options, "pluginFormatterNames", pluginFormatterNames, true);
//        FieldUtils.writeField(runtime, "runtimeOptions", options, true);

    }

    @Override
    @SuppressWarnings("unchecked")
    protected void runChild(FeatureRunner child, RunNotifier notifier) {
        Map<String, StepDefinition> stepDefinitionsByPatternTranslated = new TreeMap<>();

        try {
            Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
            RuntimeGlue glue = (RuntimeGlue) runtime.getGlue();

            cucumberFeature = (CucumberFeature) FieldUtils.readField(child, "cucumberFeature", true);
            stepDefinitionsByPattern = (Map<String, StepDefinition>) FieldUtils.readField(glue,
                    "stepDefinitionsByPattern", true);

            StepContainer currentStepContainer = (StepContainer) FieldUtils.readField(cucumberFeature, "currentStepContainer", true);
            List<Step> steps = currentStepContainer.getSteps();
            int currentStepIndex = 0;
            String lastStepContext = "";

            Queue<StepDefinition> q = new LinkedList<>();


            for (Map.Entry<String, StepDefinition> stepDefinitionEntry : stepDefinitionsByPattern.entrySet()) {
                // TODO: Выцеплять из дефинишена в каком он пакете и по этому признаку проставлять префикс
                StepDefinition stepDefinition = stepDefinitionEntry.getValue();
                Method method = (Method) FieldUtils.readField(stepDefinition, "method", true);
                String patternString = stepDefinitionEntry.getKey();
                try {
                    I18N i18n = I18N.getI18n(method.getDeclaringClass(), cucumberFeature.getI18n().getLocale(), I18N.DEFAULT_BUNDLE_PATH);
                    patternString = i18n.get(patternString);
                    if (stepDefinitionEntry.getKey().startsWith("ru.sbtqa.tag.")) {
                        for (int i = currentStepIndex; i < steps.size(); i++) {

                            Step step = steps.get(i);
                            Pattern p = Pattern.compile(patternString);
                            if (!step.getName().startsWith("ru.sbtqa.tag.") && p.matcher(step.getName()).matches()) {
                                currentStepIndex++;
                                lastStepContext = stepDefinitionEntry.getKey();
                                steps.set(i, step);
                                FieldUtils.writeField(step, "name", lastStepContext + SECRET_DELIMITER + step.getName(), true);
                            }
                        }
                        FieldUtils.writeField(currentStepContainer, "steps", steps, true);
                        FieldUtils.writeField(cucumberFeature, "currentStepContainer", currentStepContainer, true);
                        patternString = patternString.startsWith("^")
                                ? "^" + lastStepContext + SECRET_DELIMITER + patternString.substring(1)
                                : lastStepContext + SECRET_DELIMITER + patternString;

                    } else {
                        lastStepContext = "";
                    }
                    Pattern pattern = Pattern.compile(patternString);
                    FieldUtils.writeField(stepDefinition, "pattern", pattern, true);
                    FieldUtils.writeField(stepDefinition, "argumentMatcher", new JdkPatternArgumentMatcher(pattern), true);
                    stepDefinitionsByPatternTranslated.put(patternString, stepDefinition);
                } catch (I18NRuntimeException e) {
                    LOG.debug("There is no bundle for translation class. Writing as is", e);
                    stepDefinitionsByPatternTranslated.put(patternString, stepDefinition);
                }

            }

//            q.addAll(stepDefinitionsByPatternTranslated.values());

            FieldUtils.writeField(child, "cucumberFeature", cucumberFeature, true);
            FieldUtils.writeField(glue, "stepDefinitionsByPattern", stepDefinitionsByPatternTranslated, true);
            FieldUtils.writeField(runtime, "glue", glue, true);
            FieldUtils.writeField(this, "runtime", runtime, true);

            super.runChild(child, notifier);
        } catch (IllegalAccessException ex) {
            throw new CucumberException(ex);
        }
    }


    private List<StepDefinition> findUniques(Queue<StepDefinition> q) {
        List<StepDefinition> uniques = new ArrayList<>();
        while (q.peek() != null) {
            StepDefinition stepDefinition = q.remove();
            if (!q.contains(stepDefinition)) {
                uniques.add(stepDefinition);
            }
        }
        return uniques;
    }

    public static CucumberFeature getFeature() {
        return cucumberFeature;
    }

}
