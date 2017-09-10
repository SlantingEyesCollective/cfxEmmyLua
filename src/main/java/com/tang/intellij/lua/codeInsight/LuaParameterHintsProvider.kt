/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.codeInsight

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*
import java.util.*

/**

 * Created by TangZX on 2016/12/14.
 */
class LuaParameterHintsProvider : InlayParameterHintsProvider {
    private val TYPE_INFO_PREFIX = "@TYPE@"
    private var EXPR_HINT = arrayOf(LuaLiteralExpr::class.java, LuaBinaryExpr::class.java, LuaUnaryExpr::class.java, LuaClosureExpr::class.java)

    override fun getParameterHints(psi: PsiElement): List<InlayInfo> {
        val list = ArrayList<InlayInfo>()
        if (psi is LuaCallExpr) {
            @Suppress("UnnecessaryVariable")
            val callExpr = psi
            val args = callExpr.args
            val exprList = args.exprList?.exprList
            if (exprList != null) {
                val context = SearchContext(callExpr.getProject())
                val type = callExpr.guessPrefixType(context)
                val ty = TyUnion.find(type, ITyFunction::class.java) ?: return list

                // 是否是 inst:method() 被用为 inst.method(self) 形式
                val isInstanceMethodUsedAsStaticMethod = ty.isSelfCall && callExpr.isStaticMethodCall
                val isStaticMethodUsedAsInstanceMethod = !ty.isSelfCall && !callExpr.isStaticMethodCall
                var paramIndex = 0
                var argIndex = 0
                val sig = ty.findPrefectSignature(if (isInstanceMethodUsedAsStaticMethod) exprList.size - 1 else exprList.size)
                val parameters: Array<LuaParamInfo> = sig.params
                val paramCount = parameters.size

                if (isStaticMethodUsedAsInstanceMethod)
                    paramIndex = 1
                else if (isInstanceMethodUsedAsStaticMethod && !exprList.isEmpty()) {
                    val expr = exprList[argIndex++]
                    list.add(InlayInfo(Constants.WORD_SELF, expr.textOffset))
                }

                while (argIndex < exprList.size && paramIndex < paramCount) {
                    val expr = exprList[argIndex]

                    if (PsiTreeUtil.instanceOf(expr, *EXPR_HINT))
                        list.add(InlayInfo(parameters[paramIndex].name, expr.textOffset))
                    paramIndex++
                    argIndex++
                }
            }
        }
        else if (psi is LuaParamNameDef) {
            if (PARAMETER_TYPE_HINT.get()) {
                val type = psi.guessType(SearchContext(psi.project))
                if (!Ty.isInvalid(type)) {
                    return listOf(InlayInfo("$TYPE_INFO_PREFIX${type.createTypeString()}", psi.textOffset + psi.textLength))
                }
            }
        }
        else if (psi is LuaNameDef) {
            if (LOCAL_VARIABLE_HINT.get()) {
                val type = psi.guessType(SearchContext(psi.project))
                if (!Ty.isInvalid(type)) {
                    return listOf(InlayInfo("$TYPE_INFO_PREFIX${type.createTypeString()}", psi.textOffset + psi.textLength))
                }
            }
        }
        else if (psi is LuaFuncBodyOwner) {
            val paren = psi.funcBody?.rparen
            if (FUNCTION_HINT.get() && paren != null) {
                val type = psi.guessReturnTypeSet(SearchContext(psi.project))
                if (!Ty.isInvalid(type)) {
                    return listOf(InlayInfo("$TYPE_INFO_PREFIX${type.createTypeString()}", paren.textOffset + paren.textLength))
                }
            }
        }

        return list
    }

    override fun getHintInfo(psiElement: PsiElement) = null

    override fun getDefaultBlackList(): Set<String> {
        return HashSet()
    }

    override fun isBlackListSupported() = false

    private val LOCAL_VARIABLE_HINT = Option("lua.hints.show_local_var_type",
            "Show local variable type hints",
            false)
    private val PARAMETER_TYPE_HINT = Option("lua.hints.show_parameter_type",
            "Show parameter type hints",
            false)
    private val FUNCTION_HINT = Option("lua.hints.show_function_type",
            "Show function return type hints",
            false)

    override fun getSupportedOptions(): List<Option> {
        return listOf(LOCAL_VARIABLE_HINT, PARAMETER_TYPE_HINT, FUNCTION_HINT)
    }

    override fun getInlayPresentation(inlayText: String): String {
        if (inlayText.startsWith(TYPE_INFO_PREFIX)) {
            return ":${inlayText.substring(TYPE_INFO_PREFIX.length)}"
        }
        return "$inlayText:"
    }
}
