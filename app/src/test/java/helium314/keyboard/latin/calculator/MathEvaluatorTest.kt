// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.calculator

import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MathEvaluatorTest {
    private fun evaluates(expression: String, expected: String) {
        val result = assertIs<MathResult.Success>(MathEvaluator.evaluate(expression), expression)
        assertEquals(0, BigDecimal(expected).compareTo(result.value), expression)
    }

    @Test fun unaryMultiplication() = evaluates("2*-3", "-6")
    @Test fun unarySubtraction() = evaluates("1--2", "3")
    @Test fun unaryDivision() = evaluates("2/-2", "-1")
    @Test fun unaryParentheses() = evaluates("2*-(3+4)", "-14")
    @Test fun unaryPrecedence() {
        evaluates("-2^2", "-4")
        evaluates("(-2)^2", "4")
        evaluates("2^-2", "0.25")
        evaluates("--2", "2")
        evaluates("2*+3", "6")
        evaluates("2^3^2", "512")
    }

    @Test fun multiplyPercentage() = evaluates("200*10%", "20")
    @Test fun dividePercentage() = evaluates("200/10%", "2000")
    @Test fun relativePercentage() {
        evaluates("200+10%", "220")
        evaluates("200-10%", "180")
        evaluates("200+10%+10%", "242")
    }
    @Test fun percentageParenthesesAndUnaryContext() {
        evaluates("200*(5+5)%", "20")
        evaluates("200+(5+5)%", "220")
        evaluates("200+(-10%)", "180")
        evaluates("200*(-10%)", "-20")
        evaluates("200+10%*2", "200.2")
        evaluates("200+(10%+5)", "205.1")
        evaluates("200+10%^2", "200.01")
        evaluates("200*10%+5%", "21")
    }
    @Test fun malformedExpressionsAreRejected() {
        for (expression in listOf("1+2)", "(1+2", "1..2", "2garbage3", "1+", "%2")) {
            assertIs<MathResult.Error>(MathEvaluator.evaluate(expression), expression)
        }
    }

    @Test fun inlineDelimiterIsNotPartOfReplacement() {
        for (prefix in listOf("total;", "total,", "total=", "total ", "  ")) {
            val match = assertNotNull(MathEvaluator.evaluateInline("${prefix}1+2="))
            assertEquals("1+2=", match.fullMatchedText)
        }
    }
    @Test fun inlinePreservesOriginalSuffixWithWhitespace() {
        val match = assertNotNull(MathEvaluator.evaluateInline("total;1+2= \t"))
        assertEquals("1+2= \t", match.fullMatchedText)
        assertEquals("1+2", match.expression)
    }
    @Test fun inlineUnaryExpression() {
        assertEquals("-6", assertNotNull(MathEvaluator.evaluateInline("total;2*-3=")).resultFormatted)
        assertEquals("-1", assertNotNull(MathEvaluator.evaluateInline("-1+0=")).resultFormatted)
    }
}
