// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.pow

sealed class MathResult {
    data class Success(
        val value: BigDecimal,
        val formatted: String,
        val rawString: String
    ) : MathResult()

    data class Error(val message: String) : MathResult()
}

data class InlineMathMatch(
    val fullMatchedText: String,
    val expression: String,
    val resultFormatted: String,
    val resultValue: BigDecimal
)

object MathEvaluator {

    private val MATH_CONTEXT = MathContext(16, RoundingMode.HALF_UP)
    private val HUNDRED = BigDecimal("100")

    private val INLINE_MATH_REGEX = Regex(
        """(?:^|[\s=;,(])((?:[0-9]+(?:\.[0-9]+)?|\([0-9+\-*/^%.()×÷− ]+\))[0-9+\-*/^%.()×÷− ]*[0-9%)])\s*=$"""
    )

    fun evaluate(expression: String, previousAnswer: BigDecimal? = null): MathResult {
        if (expression.isBlank()) {
            return MathResult.Error("Empty expression")
        }

        try {
            val normalized = normalizeExpression(expression, previousAnswer)
            val tokens = tokenize(normalized)
            if (tokens.isEmpty()) return MathResult.Error("Empty expression")

            val rpn = shuntingYard(tokens)
            val result = evaluateRpn(rpn)
            val formatted = formatResult(result)
            return MathResult.Success(
                value = result,
                formatted = formatted,
                rawString = result.stripTrailingZeros().toPlainString()
            )
        } catch (e: ArithmeticException) {
            return MathResult.Error(e.message ?: "Math error")
        } catch (e: IllegalArgumentException) {
            return MathResult.Error(e.message ?: "Invalid expression")
        } catch (e: Exception) {
            return MathResult.Error("Error")
        }
    }

    fun evaluateInline(text: String, previousAnswer: BigDecimal? = null): InlineMathMatch? {
        if (text.isBlank() || !text.contains('=')) return null
        val trimmed = text.trim()
        val match = INLINE_MATH_REGEX.find(trimmed) ?: return null

        val exprGroup = match.groups[1]?.value?.trim() ?: return null
        // Ensure the expression actually contains at least one operator so pure "5=" doesn't trigger
        if (!exprGroup.any { it in "+-*/×÷−^%" }) return null

        val result = evaluate(exprGroup, previousAnswer)
        if (result is MathResult.Success) {
            return InlineMathMatch(
                fullMatchedText = match.value.trimStart(),
                expression = exprGroup,
                resultFormatted = result.formatted,
                resultValue = result.value
            )
        }
        return null
    }

    fun formatResult(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val stripped = value.stripTrailingZeros()
        val absVal = stripped.abs()
        val isZero = stripped.compareTo(BigDecimal.ZERO) == 0

        if (isZero) return "0"

        val plain = stripped.toPlainString()
        // If huge (> 10^14) or tiny (< 10^-5), format with scientific notation
        if (absVal >= BigDecimal("100000000000000") || (absVal <= BigDecimal("0.00001") && !isZero)) {
            val symbols = DecimalFormatSymbols(locale)
            val df = DecimalFormat("0.######E0", symbols)
            return df.format(stripped.toDouble())
        }

        return plain
    }

    private fun normalizeExpression(raw: String, previousAnswer: BigDecimal?): String {
        var expr = raw
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace(',', '.')
            .replace(" ", "")

        if (previousAnswer != null) {
            val ansStr = previousAnswer.stripTrailingZeros().toPlainString()
            expr = expr.replace("Ans", ansStr).replace("ans", ansStr)
        } else {
            expr = expr.replace("Ans", "0").replace("ans", "0")
        }

        // Insert implicit multiplication: e.g. 5( -> 5*( , )( -> )*( , )5 -> )*5
        val sb = StringBuilder()
        for (i in expr.indices) {
            val c = expr[i]
            if (i > 0) {
                val prev = expr[i - 1]
                if ((prev.isDigit() || prev == ')' || prev == '%') && c == '(') {
                    sb.append('*')
                } else if (prev == ')' && (c.isDigit() || c == '.')) {
                    sb.append('*')
                }
            }
            sb.append(c)
        }
        return sb.toString()
    }

    private sealed class Token {
        data class Number(val value: BigDecimal) : Token()
        data class Op(val symbol: Char, val precedence: Int, val isRightAssociative: Boolean = false) : Token()
        data object OpenParen : Token()
        data object CloseParen : Token()
        data class Percent(val isStandalone: Boolean = false) : Token()
    }

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var expectUnary = true

        while (i < expr.length) {
            val c = expr[i]

            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    var hasDot = (c == '.')
                    i++
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                        if (expr[i] == '.') {
                            if (hasDot) break
                            hasDot = true
                        }
                        i++
                    }
                    val numStr = expr.substring(start, i)
                    val num = try {
                        BigDecimal(if (numStr == ".") "0" else numStr)
                    } catch (_: Exception) {
                        throw IllegalArgumentException("Invalid number: $numStr")
                    }
                    tokens.add(Token.Number(num))
                    expectUnary = false
                }
                c == '(' -> {
                    tokens.add(Token.OpenParen)
                    expectUnary = true
                    i++
                }
                c == ')' -> {
                    tokens.add(Token.CloseParen)
                    expectUnary = false
                    i++
                }
                c == '%' -> {
                    tokens.add(Token.Percent())
                    expectUnary = false
                    i++
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' -> {
                    if (expectUnary) {
                        if (c == '-') {
                            // Unary minus: represented as 0 - x or negated token
                            tokens.add(Token.Number(BigDecimal.ZERO))
                            tokens.add(Token.Op('-', 1))
                            i++
                            expectUnary = true
                            continue
                        } else if (c == '+') {
                            // Unary plus is ignored
                            i++
                            continue
                        } else {
                            throw IllegalArgumentException("Unexpected operator: $c")
                        }
                    }

                    val precedence = when (c) {
                        '+', '-' -> 1
                        '*', '/' -> 2
                        '^' -> 3
                        else -> 0
                    }
                    val rightAssoc = (c == '^')
                    tokens.add(Token.Op(c, precedence, rightAssoc))
                    expectUnary = true
                    i++
                }
                else -> {
                    i++
                }
            }
        }
        return tokens
    }

    private fun shuntingYard(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val opStack = ArrayDeque<Token>()

        for (token in tokens) {
            when (token) {
                is Token.Number -> output.add(token)
                is Token.Percent -> output.add(token)
                is Token.Op -> {
                    while (opStack.isNotEmpty()) {
                        val top = opStack.peek()
                        if (top is Token.Op) {
                            if ((!token.isRightAssociative && token.precedence <= top.precedence) ||
                                (token.isRightAssociative && token.precedence < top.precedence)
                            ) {
                                output.add(opStack.pop())
                            } else break
                        } else break
                    }
                    opStack.push(token)
                }
                is Token.OpenParen -> opStack.push(token)
                is Token.CloseParen -> {
                    var foundOpen = false
                    while (opStack.isNotEmpty()) {
                        val top = opStack.pop()
                        if (top is Token.OpenParen) {
                            foundOpen = true
                            break
                        } else {
                            output.add(top)
                        }
                    }
                    if (!foundOpen) {
                        // Mismatched closing paren is ignored gracefully
                    }
                }
            }
        }

        while (opStack.isNotEmpty()) {
            val top = opStack.pop()
            if (top !is Token.OpenParen && top !is Token.CloseParen) {
                output.add(top)
            }
        }

        return output
    }

    private fun evaluateRpn(rpn: List<Token>): BigDecimal {
        val stack = ArrayDeque<BigDecimal>()

        for (token in rpn) {
            when (token) {
                is Token.Number -> stack.push(token.value)
                is Token.Percent -> {
                    if (stack.isEmpty()) throw IllegalArgumentException("Missing operand for %")
                    val current = stack.pop()
                    val prev = if (stack.isNotEmpty()) stack.peek() else null
                    if (prev != null) {
                        val relative = prev.multiply(current, MATH_CONTEXT).divide(HUNDRED, MATH_CONTEXT)
                        stack.push(relative)
                    } else {
                        stack.push(current.divide(HUNDRED, MATH_CONTEXT))
                    }
                }
                is Token.Op -> {
                    if (stack.size < 2) throw IllegalArgumentException("Invalid expression format")
                    val b = stack.pop()
                    val a = stack.pop()
                    val res = when (token.symbol) {
                        '+' -> a.add(b, MATH_CONTEXT)
                        '-' -> a.subtract(b, MATH_CONTEXT)
                        '*' -> a.multiply(b, MATH_CONTEXT)
                        '/' -> {
                            if (b.compareTo(BigDecimal.ZERO) == 0) {
                                throw ArithmeticException("Cannot divide by zero")
                            }
                            a.divide(b, MATH_CONTEXT)
                        }
                        '^' -> {
                            val bDouble = b.toDouble()
                            if (b.scale() <= 0 || b.stripTrailingZeros().scale() <= 0) {
                                val intExp = b.toInt()
                                if (intExp in -999..999) {
                                    if (intExp < 0) {
                                        BigDecimal.ONE.divide(a.pow(-intExp, MATH_CONTEXT), MATH_CONTEXT)
                                    } else {
                                        a.pow(intExp, MATH_CONTEXT)
                                    }
                                } else {
                                    BigDecimal(a.toDouble().pow(bDouble), MATH_CONTEXT)
                                }
                            } else {
                                BigDecimal(a.toDouble().pow(bDouble), MATH_CONTEXT)
                            }
                        }
                        else -> throw IllegalArgumentException("Unknown operator: ${token.symbol}")
                    }
                    stack.push(res)
                }
                else -> {}
            }
        }

        if (stack.isEmpty()) throw IllegalArgumentException("Empty evaluation result")
        return stack.pop()
    }
}
