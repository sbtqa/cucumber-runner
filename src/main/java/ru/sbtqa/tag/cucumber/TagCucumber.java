package ru.sbtqa.tag.cucumber;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import cucumber.runtime.CucumberException;
import cucumber.runtime.JdkPatternArgumentMatcher;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.StepDefinition;
import cucumber.runtime.junit.ExecutionUnitRunner;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberScenario;
import cucumber.runtime.model.StepContainer;
import gherkin.formatter.model.Step;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.slf4j.LoggerFactory;
import ru.sbtqa.tag.qautils.i18n.I18N;
import ru.sbtqa.tag.qautils.i18n.I18NRuntimeException;

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
    private static final String PLUGIN_PACKAGE = "ru.sbtqa.tag.";
    private static final String STRING_START_REGEX = "^";

    public static final String SECRET_DELIMITER = "°\u0000\u0000\u0000 ";

    private static final ThreadLocal<CucumberFeature> CUCUMBER_FEATURE = new ThreadLocal<>();
    private static final ThreadLocal<CurrentClass> CURRENT_CLASS = new ThreadLocal<>();

    /**
     * Constructor called by JUnit.
     *
     * @param clazz the class with the @RunWith annotation.
     * @throws java.io.IOException if there is a problem
     * @throws org.junit.runners.model.InitializationError if there is another problem
     * @throws java.lang.IllegalAccessException if any reflection error
     */
    @SuppressWarnings("unchecked")
    public TagCucumber(Class clazz) throws InitializationError, IOException, IllegalAccessException {
        super(clazz);
        CURRENT_CLASS.set(new CurrentClass(this.getTestClass()));

    }

    @Override
    @SuppressWarnings("unchecked")
    protected void runChild(FeatureRunner child, RunNotifier notifier) {
        Map<String, StepDefinition> stepDefinitionsByPatternTranslated = new TreeMap<>();

        try {
            Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
            RuntimeGlue glue = (RuntimeGlue) runtime.getGlue();

            CUCUMBER_FEATURE.set((CucumberFeature) FieldUtils.readField(child, "cucumberFeature", true));
            List<ExecutionUnitRunner> children = ((ArrayList<ExecutionUnitRunner>) FieldUtils.readField(child, "children", true));
            Map<String, StepDefinition> stepDefinitionsByPattern = (Map<String, StepDefinition>) FieldUtils.readField(glue, "stepDefinitionsByPattern", true);

            StepContainer currentStepContainer = (StepContainer) FieldUtils.readField(CUCUMBER_FEATURE.get(), "currentStepContainer", true);
            List<Step> steps = currentStepContainer.getSteps();


            if (CURRENT_CLASS.get().isTranslated(this.getTestClass())) {
                stepDefinitionsByPatternTranslated = stepDefinitionsByPattern;
            } else {
                // Проставляем префикс в зависимости от пакета с секретом
                for (Map.Entry<String, StepDefinition> stepDefinitionEntry : stepDefinitionsByPattern.entrySet()) {

                    StepDefinition stepDefinition = stepDefinitionEntry.getValue();
                    Method method = (Method) FieldUtils.readField(stepDefinition, "method", true);
                    String patternString = stepDefinitionEntry.getKey();

                    try {
                        Class<?> declaringClass = method.getDeclaringClass();
                        I18N i18n = I18N.getI18n(declaringClass, CUCUMBER_FEATURE.get().getI18n().getLocale(), I18N.DEFAULT_BUNDLE_PATH);
                        patternString = i18n.get(patternString);
                        String canonicalName = declaringClass.getCanonicalName();
                        String context;
                        if (canonicalName.contains(PLUGIN_PACKAGE)) {
                            context = canonicalName.substring(PLUGIN_PACKAGE.length(), canonicalName.indexOf('.', "ru.sbtqa.tag.".length()));

                            String translatedPattern = STRING_START_REGEX + context + SECRET_DELIMITER +
                                    (patternString.startsWith(STRING_START_REGEX) ? patternString.substring(1) : patternString);

                            stepDefinitionsByPatternTranslated.put(translatedPattern, stepDefinition);
                            Pattern pattern = Pattern.compile(translatedPattern);
                            FieldUtils.writeField(stepDefinition, "pattern", pattern, true);
                            FieldUtils.writeField(stepDefinition, "argumentMatcher", new JdkPatternArgumentMatcher(pattern), true);
                        }
                    } catch (I18NRuntimeException e) {
                        LOG.debug("There is no bundle for translation class. Writing it as is.", e);
                        stepDefinitionsByPatternTranslated.put(patternString, stepDefinition);
                    }
                }
                CURRENT_CLASS.set(new CurrentClass(this.getTestClass()).markTranslated());
            }

            List<ExecutionUnitRunner> newChildren = new ArrayList<>();
            for (ExecutionUnitRunner childRunner : children) {
                FieldUtils.writeField(childRunner, "runnerSteps",
                        this.processSteps(childRunner.getRunnerSteps(), stepDefinitionsByPatternTranslated), true);

                CucumberScenario cucumberScenario = (CucumberScenario) FieldUtils.readField(childRunner, "cucumberScenario", true);
                FieldUtils.writeField(cucumberScenario, "steps",
                        this.processSteps(cucumberScenario.getSteps(), stepDefinitionsByPatternTranslated), true);
                FieldUtils.writeField(childRunner, "cucumberScenario", cucumberScenario, true);


                newChildren.add(childRunner);

            }


            FieldUtils.writeField(currentStepContainer, "steps", this.processSteps(steps, stepDefinitionsByPatternTranslated), true);
            FieldUtils.writeField(CUCUMBER_FEATURE.get(), "currentStepContainer", currentStepContainer, true);
            FieldUtils.writeField(child, "children", newChildren, true);
            FieldUtils.writeField(child, "cucumberFeature", CUCUMBER_FEATURE.get(), true);
            FieldUtils.writeField(glue, "stepDefinitionsByPattern", stepDefinitionsByPatternTranslated, true);
            FieldUtils.writeField(runtime, "glue", glue, true);
            FieldUtils.writeField(this, "runtime", runtime, true);

            super.runChild(child, notifier);
        } catch (IllegalAccessException ex) {
            throw new CucumberException(ex);
        }
    }


    private List<Step> processSteps(List<Step> steps, Map<String, StepDefinition> stepDefinitionsByPatternTranslated) throws IllegalAccessException {
        // Processing steps
        List<String> matchedStepDefsPatterns = new ArrayList<>();
        Step step;
        for (int i = 0; i < steps.size(); i++) {
            matchedStepDefsPatterns.clear();
            step = steps.get(i);

            // Determine how many regexes conforms step's pattern
            String stepName = step.getName();
            for (Map.Entry<String, StepDefinition> stringStepDefinitionEntry : stepDefinitionsByPatternTranslated.entrySet()) {
                if (Pattern.compile(getPattern(stringStepDefinitionEntry.getValue().getPattern())).matcher(stepName).matches()) {
                    matchedStepDefsPatterns.add(stringStepDefinitionEntry.getKey());
                }
            }

            if (matchedStepDefsPatterns.isEmpty()) {
                continue;
//                throw new RuntimeException(String.format("There isn't step definition matched to step %s", step.getName()));
            }

            if (matchedStepDefsPatterns.size() == 1) {
                // If it contains SECRET_DELIMITER so it TAG's plugin step, else it from end project
                if (matchedStepDefsPatterns.get(0).contains(SECRET_DELIMITER)) {
                    FieldUtils.writeField(step, "name", getContext(matchedStepDefsPatterns.get(0)) +
                            SECRET_DELIMITER + stepName, true);
                }
            } else {
                // Any conflicts should be fixed in TAG plugins, we should avoid it
                // Getting context from previous step
                String context = getContext(steps.get(i - 1).getName());
                boolean isMatched = false;
                for (String matchedStepDefsPattern : matchedStepDefsPatterns) {
                    if (matchedStepDefsPattern.contains(context)) {
                        isMatched = true;
                        FieldUtils.writeField(step, "name", context + SECRET_DELIMITER + stepName, true);
                    }
                }
                // In case several stepdefs found which conforms given patters, and there is no stepdef
                // with needed context? RuntimeException should be thrown
                if (!isMatched) {
                    throw new RuntimeException(String.format("There isn't step %s in context %s", step.getName(), context));
                }
            }
            steps.set(i, step);
        }
        return steps;
    }

    private String getContext(String reg) {
        reg = reg.startsWith(STRING_START_REGEX) ? reg.substring(1) : reg;
        String[] split = reg.split(SECRET_DELIMITER);
        return split.length > 1 ? split[0] : "";
    }

    private String getPattern(String reg) {
        String[] split = reg.split(SECRET_DELIMITER);
        return split.length > 1 ? STRING_START_REGEX + split[1] : reg;
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
        return CUCUMBER_FEATURE.get();
    }

}
