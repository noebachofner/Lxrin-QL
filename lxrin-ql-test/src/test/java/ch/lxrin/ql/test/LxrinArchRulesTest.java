package ch.lxrin.ql.test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LxrinArchRulesTest {

    private static final JavaClasses ALL = new ClassFileImporter().importPackages("com.example.archsample");
    private static final JavaClasses GOOD = new ClassFileImporter().importPackages("com.example.archsample.good");

    private static String violations(EvaluationResult result) {
        return String.join("\n", result.getFailureReport().getDetails());
    }

    @Test
    void rawSqlIsDetected() {
        EvaluationResult result = LxrinArchRules.noRawSql().evaluate(ALL);
        assertTrue(result.hasViolation());
        assertTrue(violations(result).contains("RawQueries"));
        assertFalse(LxrinArchRules.noRawSql().evaluate(GOOD).hasViolation());
        assertFalse(LxrinArchRules.noRawSqlOutside("com.example.archsample.bad..").evaluate(ALL).hasViolation());
    }

    @Test
    void handWrittenQueryPartsAreDetected() {
        EvaluationResult result = LxrinArchRules.noHandWrittenQueryParts().evaluate(ALL);
        assertTrue(violations(result).contains("RawQueries"));
        assertFalse(LxrinArchRules.noHandWrittenQueryParts().evaluate(GOOD).hasViolation());
    }

    @Test
    void policyBypassIsRestricted() {
        EvaluationResult result = LxrinArchRules.policyBypassOnlyIn("com.example.archsample.admin..").evaluate(ALL);
        assertTrue(violations(result).contains("BypassEverywhere"));
        assertFalse(violations(result).contains("AdminQueries"));
    }

    @Test
    void jdbcIsRestricted() {
        assertTrue(LxrinArchRules.noJdbcOutside("com.example.archsample.good..").evaluate(ALL).hasViolation());
        assertFalse(LxrinArchRules.noJdbcOutside("com.example.archsample.bad..").evaluate(ALL).hasViolation());
    }
}
