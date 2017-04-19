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

    static final String SECRET_DELIMITER = ":::==!!SECRET!!==:::";

    public static CucumberFeature cucumberFeature;

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
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void runChild(FeatureRunner child, RunNotifier notifier) {
        Map<String, StepDefinition> stepDefinitionsByPatternTranslated = new TreeMap<>();

        try {
            Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
            RuntimeGlue glue = (RuntimeGlue) runtime.getGlue();

            cucumberFeature = (CucumberFeature) FieldUtils.readField(child, "cucumberFeature", true);
            Map<String, StepDefinition> stepDefinitionsByPattern = (Map<String, StepDefinition>) FieldUtils.readField(glue, "stepDefinitionsByPattern", true);

            StepContainer currentStepContainer = (StepContainer) FieldUtils.readField(cucumberFeature, "currentStepContainer", true);
            List<Step> steps = currentStepContainer.getSteps();


            // Проставляем префикс в зависимости от пакета с секретом
            for (Map.Entry<String, StepDefinition> stepDefinitionEntry : stepDefinitionsByPattern.entrySet()) {

                StepDefinition stepDefinition = stepDefinitionEntry.getValue();
                Method method = (Method) FieldUtils.readField(stepDefinition, "method", true);
                String patternString = stepDefinitionEntry.getKey();

                try {
                    Class<?> declaringClass = method.getDeclaringClass();
                    I18N i18n = I18N.getI18n(declaringClass, cucumberFeature.getI18n().getLocale(), I18N.DEFAULT_BUNDLE_PATH);
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
                    throw new RuntimeException();
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
                    for (String matchedStepDefsPattern : matchedStepDefsPatterns) {
                        if (matchedStepDefsPattern.contains(context)) {
                            FieldUtils.writeField(step, "name", context + SECRET_DELIMITER + stepName, true);
                        }
                    }
                }
            }

            FieldUtils.writeField(currentStepContainer, "steps", steps, true);
            FieldUtils.writeField(cucumberFeature, "currentStepContainer", currentStepContainer, true);
            FieldUtils.writeField(child, "cucumberFeature", cucumberFeature, true);
            FieldUtils.writeField(glue, "stepDefinitionsByPattern", stepDefinitionsByPatternTranslated, true);
            FieldUtils.writeField(runtime, "glue", glue, true);
            FieldUtils.writeField(this, "runtime", runtime, true);

            super.runChild(child, notifier);
        } catch (IllegalAccessException ex) {
            throw new CucumberException(ex);
        }
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
        return cucumberFeature;
    }

}
