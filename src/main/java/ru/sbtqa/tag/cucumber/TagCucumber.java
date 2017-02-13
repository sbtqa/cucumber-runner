package ru.sbtqa.tag.cucumber;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import cucumber.runtime.*;
import cucumber.runtime.Runtime;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.model.CucumberFeature;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.slf4j.LoggerFactory;
import ru.sbtqa.tag.qautils.i18n.I18N;
import ru.sbtqa.tag.qautils.i18n.I18NRuntimeException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
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
    private static final ThreadLocal<CucumberFeature> cucumberFeature = new ThreadLocal<>();
    private Map<String, StepDefinition> stepDefinitionsByPattern;


    /**
     * Constructor called by JUnit.
     *
     * @param clazz the class with the @RunWith annotation.
     * @throws java.io.IOException if there is a problem
     * @throws org.junit.runners.model.InitializationError if there is another
     * problem
     * @throws java.lang.IllegalAccessException if any reflection error
     */
    public TagCucumber(Class clazz) throws InitializationError, IOException, IllegalAccessException {
        super(clazz);

        Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
        RuntimeGlue glue = (RuntimeGlue) runtime.getGlue();
        stepDefinitionsByPattern = (Map<String, StepDefinition>) FieldUtils.readField(glue,
                "stepDefinitionsByPattern", true);
    }

    /**
     * Returns current running feature. It is thread safe static method
     * @return {@link CucumberFeature} current running feature
     */
    public static CucumberFeature getFeature() {
        return cucumberFeature.get();
    }

    @Override
    protected void runChild(FeatureRunner child, RunNotifier notifier) {
        Map<String, StepDefinition> stepDefinitionsByPatternTranslated = new TreeMap<>();

        try {
            Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
            RuntimeGlue glue = (RuntimeGlue) runtime.getGlue();

            cucumberFeature.set((CucumberFeature) FieldUtils.readField(child, "cucumberFeature", true));
            stepDefinitionsByPattern = (Map<String, StepDefinition>) FieldUtils.readField(glue,
                    "stepDefinitionsByPattern", true);

            for (Map.Entry<String, StepDefinition> stepDefinitionEntry : stepDefinitionsByPattern.entrySet()) {

                StepDefinition stepDefinition = stepDefinitionEntry.getValue();
                Method method = (Method) FieldUtils.readField(stepDefinition, "method", true);
                String patternString = stepDefinitionEntry.getKey();
                try {
                    I18N i18n = I18N.getI18n(method.getDeclaringClass(), getFeature().getI18n().getLocale(), I18N.DEFAULT_BUNDLE_PATH);
                    patternString = i18n.get(patternString);
                    Pattern pattern = Pattern.compile(patternString);
                    FieldUtils.writeField(stepDefinition, "pattern", pattern, true);
                    FieldUtils.writeField(stepDefinition, "argumentMatcher", new JdkPatternArgumentMatcher(pattern), true);
                    stepDefinitionsByPatternTranslated.put(patternString, stepDefinition);
                } catch (I18NRuntimeException e) {
                    LOG.debug("There is no bundle for translation class. Writing as is", e);
                    stepDefinitionsByPatternTranslated.put(patternString, stepDefinition);
                }

            }

            FieldUtils.writeField(glue, "stepDefinitionsByPattern", stepDefinitionsByPatternTranslated, true);
            FieldUtils.writeField(runtime, "glue", glue, true);
            FieldUtils.writeField(this, "runtime", runtime, true);

            super.runChild(child, notifier);
        } catch (IllegalAccessException ex) {
            throw new CucumberException(ex);
        }
    }
}
