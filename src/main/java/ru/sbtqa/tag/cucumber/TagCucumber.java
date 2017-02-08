package ru.sbtqa.tag.cucumber;

import cucumber.api.junit.Cucumber;
import cucumber.runtime.CucumberException;
import cucumber.runtime.JdkPatternArgumentMatcher;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.StepDefinition;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.model.CucumberFeature;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.runners.model.InitializationError;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.junit.runner.notification.RunNotifier;
import org.slf4j.LoggerFactory;
import ru.sbtqa.tag.qautils.i18n.I18N;
import ru.sbtqa.tag.qautils.i18n.I18NRuntimeException;
import ru.sbtqa.tag.qautils.properties.Props;
import ru.sbtqa.tag.qautils.properties.PropsRuntimeException;

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
    private static Map<String, StepDefinition> stepDefinitionsByPattern;

    /**
     * Constructor called by JUnit.
     *
     * @param clazz the class with the @RunWith annotation.
     * @throws java.io.IOException if there is a problem
     * @throws org.junit.runners.model.InitializationError if there is another
     * problem
     */
    public TagCucumber(Class clazz) throws InitializationError, IOException, IllegalAccessException {
        super(clazz);

        Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
        RuntimeGlue glue = (RuntimeGlue) runtime.getGlue();
        stepDefinitionsByPattern = (Map<String, StepDefinition>) FieldUtils.readField(glue,
                "stepDefinitionsByPattern", true);
    }

    @Override
    protected void runChild(FeatureRunner child, RunNotifier notifier) {

        try {
            Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
            RuntimeGlue glue = (RuntimeGlue) runtime.getGlue();
            stepDefinitionsByPattern = (Map<String, StepDefinition>) FieldUtils.readField(glue,
                    "stepDefinitionsByPattern", true);

            Map<String, StepDefinition> stepDefinitionsByPatternTeranslated = new TreeMap<>();

            for (Map.Entry<String, StepDefinition> stepDefinitionEntry : stepDefinitionsByPattern.entrySet()) {

                CucumberFeature cucumberFeature = (CucumberFeature) FieldUtils.readField(child, "cucumberFeature", true);

                StepDefinition stepDefinition = stepDefinitionEntry.getValue();

                Method method = (Method) FieldUtils.readField(stepDefinition, "method", true);
                String bundlePath = I18N.DEFAULT_BUNDLE_PATH;
                try {
                    bundlePath = Props.get("i18n.path", I18N.DEFAULT_BUNDLE_PATH);
                } catch (PropsRuntimeException e) {
                    LOG.debug("Properties file does not exist failing back to default bundle path \"{}\"", I18N.DEFAULT_BUNDLE_PATH, e);
                }

                String patternString = stepDefinitionEntry.getKey();
                try {
                    I18N i18n = I18N.getI18n(method.getDeclaringClass(), cucumberFeature.getI18n().getLocale(), bundlePath);
                    patternString = i18n.get(patternString);
                    Pattern pattern = Pattern.compile(patternString);
                    FieldUtils.writeField(stepDefinition, "pattern", pattern, true);
                    FieldUtils.writeField(stepDefinition, "argumentMatcher", new JdkPatternArgumentMatcher(pattern), true);
                    stepDefinitionsByPatternTeranslated.put(patternString, stepDefinition);
                } catch (I18NRuntimeException e) {
                    LOG.debug("There is no bundle for translation class. Writing as is", e);
                    stepDefinitionsByPatternTeranslated.put(patternString, stepDefinition);
                }

            }

            FieldUtils.writeField(glue, "stepDefinitionsByPattern", stepDefinitionsByPatternTeranslated, true);
            FieldUtils.writeField(runtime, "glue", glue, true);
            FieldUtils.writeField(this, "runtime", runtime, true);

            super.runChild(child, notifier);
        } catch (IllegalAccessException ex) {
            throw new CucumberException(ex);
        }
    }
}
