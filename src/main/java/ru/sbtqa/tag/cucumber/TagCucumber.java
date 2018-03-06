package ru.sbtqa.tag.cucumber;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import cucumber.runtime.CucumberException;
import cucumber.runtime.JdkPatternArgumentMatcher;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.StepDefinition;
import cucumber.runtime.junit.ExamplesRunner;
import cucumber.runtime.junit.ExecutionUnitRunner;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.model.CucumberBackground;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberScenario;
import cucumber.runtime.model.StepContainer;
import gherkin.formatter.model.Step;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Constructor called by JUnit.
     *
     * @param clazz the class with the @RunWith annotation.
     * @throws java.io.IOException if there is a problem
     * @throws org.junit.runners.model.InitializationError if there is another problem
     */
    @SuppressWarnings("unchecked")
    public TagCucumber(Class clazz) throws InitializationError, IOException {
        super(clazz);
        LOG.warn("TagCucumber is deprecated and going to be deleted soon. Use latest version of Page-Factory and default Cucumber runner.");
    }
}
