// Copyright © 2016 Laurence Gonsalves
//
// This file is part of kotlin-argparser, a library which can be found at
// http://github.com/xenomachina/kotlin-argparser
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation; either version 2.1 of the License, or (at your
// option) any later version.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, see http://www.gnu.org/licenses/

package com.xenomachina.argparser

import com.xenomachina.argparser.PosixNaming.identifierToArgName
import com.xenomachina.argparser.PosixNaming.identifierToOptionName
import com.xenomachina.argparser.PosixNaming.optionNameToArgName
import com.xenomachina.argparser.PosixNaming.selectRepresentativeOptionName
import com.xenomachina.common.Holder
import com.xenomachina.common.orElse
import java.util.*
import kotlin.reflect.KProperty

/**
 * A command-line option/argument parser.
 *
 * @param args the command line arguments to parse
 * @param mode parsing mode, defaults to GNU-style parsing
 * @param skippingUnrecognizedArgs skipping argument and it's value not declared by delegate. see [eatUnrecognizedArgs].
 * @param helpFormatter if non-null, creates `--help` and `-h` options that trigger a [ShowHelpException] which will use
 * the supplied [HelpFormatter] to generate a help message.
 */
class ArgParser(
    args: Array<out String>,
    val mode: Mode = Mode.GNU,
    helpFormatter: HelpFormatter? = DefaultHelpFormatter(),
    val skippingUnrecognizedArgs: Boolean = false
) {

    enum class Mode {
        /** For GNU-style option parsing, where options may appear after positional arguments. */
        GNU,

        /** For POSIX-style option parsing, where options must appear before positional arguments. */
        POSIX
    }

    /**
     * Creates a Delegate for a zero-argument option that returns true if and only the option is present in args.
     */
    fun flagging(vararg names: String, help: String): Delegate<Boolean> =
            option<Boolean>(
                    *names,
                    help = help) { true }.default(false)

    /**
     * Creates a DelegateProvider for a zero-argument option that returns true if and only the option is present in
     * args.
     */
    fun flagging(help: String) =
            DelegateProvider { identifier -> flagging(identifierToOptionName(identifier), help = help) }

    /**
     * Creates a Delegate for a zero-argument option that returns the count of how many times the option appears in
     * args.
     */
    fun counting(vararg names: String, help: String): Delegate<Int> =
            option<Int>(
                    *names,
                    isRepeating = true,
                    help = help) { value.orElse { 0 } + 1 }.default(0)

    /**
     * Creates a DelegateProvider for a zero-argument option that returns the count of how many times the option appears
     * in args.
     */
    fun counting(help: String) = DelegateProvider {
        identifier -> counting(identifierToOptionName(identifier), help = help)
    }

    /**
     * Creates a Delegate for a single-argument option that stores and returns the option's (transformed) argument.
     */
    fun <T> storing(
        vararg names: String,
        help: String,
        argName: String? = null,
        transform: String.() -> T
    ): Delegate<T> {
        val nonNullArgName = argName ?: optionNameToArgName(selectRepresentativeOptionName(names))
        return option(
                *names,
                errorName = nonNullArgName,
                argNames = listOf(nonNullArgName),
                help = help) { transform(arguments.first()) }
    }

    /**
     * Creates a DelegateProvider for a single-argument option that stores and returns the option's (transformed)
     * argument.
     */
    fun <T> storing(
        help: String,
        argName: String? = null,
        transform: String.() -> T
    ) = DelegateProvider { identifier ->
        storing(identifierToOptionName(identifier), help = help, argName = argName, transform = transform)
    }

    /**
     * Creates a Delegate for a single-argument option that stores and returns the option's argument.
     */
    fun storing(vararg names: String, help: String, argName: String? = null): Delegate<String> =
            storing(*names, help = help, argName = argName) { this }

    /**
     * Creates a DelegateProvider for a single-argument option that stores and returns the option's argument.
     */
    fun storing(help: String, argName: String? = null) =
            DelegateProvider { identifier ->
                storing(identifierToOptionName(identifier), help = help, argName = argName) }

    /**
     * Creates a Delegate for a single-argument option that adds the option's (transformed) argument to a
     * MutableCollection each time the option appears in args, and returns said MutableCollection.
     */
    fun <E, T : MutableCollection<E>> adding(
        vararg names: String,
        help: String,
        argName: String? = null,
        initialValue: T,
        transform: String.() -> E
    ): Delegate<T> {
        val nonNullArgName = argName ?: optionNameToArgName(selectRepresentativeOptionName(names))
        return option<T>(
                *names,
                help = help,
                argNames = listOf(nonNullArgName),
                isRepeating = true) {
            val result = value.orElse { initialValue }
            result.add(transform(arguments.first()))
            result
        }.default(initialValue)
    }

    /**
     * Creates a DelegateProvider for a single-argument option that adds the option's (transformed) argument to a
     * MutableCollection each time the option appears in args, and returns said MutableCollection.
     */
    fun <E, T : MutableCollection<E>> adding(
        help: String,
        argName: String? = null,
        initialValue: T,
        transform: String.() -> E
    ) = DelegateProvider { identifier ->
        adding(
            identifierToOptionName(identifier),
            help = help,
            argName = argName,
            initialValue = initialValue,
            transform = transform)
    }

    /**
     * Creates a Delegate for a single-argument option that adds the option's (transformed) argument to a
     * MutableList each time the option appears in args, and returns said MutableCollection.
     */
    fun <T> adding(
        vararg names: String,
        help: String,
        argName: String? = null,
        transform: String.() -> T
    ) = adding(*names, help = help, argName = argName, initialValue = mutableListOf(), transform = transform)

    /**
     * Creates a DelegateProvider for a single-argument option that adds the option's (transformed) argument to a
     * MutableList each time the option appears in args, and returns said MutableCollection.
     */
    fun <T> adding(
        help: String,
        argName: String? = null,
        transform: String.() -> T
    ) = DelegateProvider { identifier ->
        adding(identifierToOptionName(identifier), help = help, argName = argName, transform = transform) }

    /**
     * Creates a Delegate for a single-argument option that adds the option's argument to a MutableList each time the
     * option appears in args, and returns said MutableCollection.
     */
    fun adding(vararg names: String, help: String, argName: String? = null): Delegate<MutableList<String>> =
            adding(*names, help = help, argName = argName) { this }

    /**
     * Creates a DelegateProvider for a single-argument option that adds the option's argument to a MutableList each
     * time the option appears in args, and returns said MutableCollection.
     */
    fun adding(help: String) = DelegateProvider { identifier ->
        adding(identifierToOptionName(identifier), help = help) }

    /**
     * Creates a Delegate for a zero-argument option that maps from the option's name as it appears in args to one of a
     * fixed set of values.
     */
    fun <T> mapping(vararg pairs: Pair<String, T>, help: String): Delegate<T> =
            mapping(mapOf(*pairs), help = help)

    /**
     * Creates a Delegate for a zero-argument option that maps from the option's name as it appears in args to one of a
     * fixed set of values.
     */
    fun <T> mapping(map: Map<String, T>, help: String): Delegate<T> {
        val names = map.keys.toTypedArray()
        return option(*names,
                errorName = map.keys.joinToString("|"),
                help = help) {
            // This cannot be null, because the optionName was added to the map
            // at the same time it was registered with the ArgParser.
            map[optionName]!!
        }
    }

    /**
     * Creates a Delegate for an option with the specified names.
     * @param names names of options, with leading "-" or "--"
     * @param errorName name to use when talking about this option in error messages, or null to base it upon the
     * option names
     * @param help the help text for this option
     * @param argNames names of this option's arguments
     * @param isRepeating whether or not it makes sense to repeat this option -- usually used for options where
     * specifying the option more than once yields a value than cannot be expressed by specifying the option only once
     * @param handler a function that computes the value of this option from an [OptionInvocation]
     */
    fun <T> option(
        // TODO: add optionalArg: Boolean
        vararg names: String,
        help: String,
        errorName: String? = null,
        argNames: List<String> = emptyList(),
        isRepeating: Boolean = false,
        handler: OptionInvocation<T>.() -> T
    ): Delegate<T> = OptionDelegate(
        parser = this,
        errorName = errorName ?: optionNameToArgName(selectRepresentativeOptionName(names)),
        help = help,
        optionNames = listOf(*names),
        argNames = argNames.toList(),
        isRepeating = isRepeating,
        handler = handler
    )

    /**
     * Creates a Delegate for a single positional argument which returns the argument's value.
     */
    fun positional(name: String, help: String) = positional(name, help = help) { this }

    /**
     * Creates a DelegateProvider for a single positional argument which returns the argument's value.
     */
    fun positional(help: String) =
            DelegateProvider { identifier -> positional(identifierToArgName(identifier), help = help) }

    /**
     * Creates a Delegate for a single positional argument which returns the argument's transformed value.
     */
    fun <T> positional(
        name: String,
        help: String,
        transform: String.() -> T
    ): Delegate<T> {
        return WrappingDelegate(
                positionalList(name, help = help, sizeRange = 1..1, transform = transform)
        ) { it[0] }
    }

    /**
     * Creates a DelegateProvider for a single positional argument which returns the argument's transformed value.
     */
    fun <T> positional(
        help: String,
        transform: String.() -> T
    ) = DelegateProvider { identifier ->
        positional(identifierToArgName(identifier), help = help, transform = transform)
    }

    /**
     * Creates a Delegate for a sequence of positional arguments which returns a List containing the arguments.
     */
    fun positionalList(
        name: String,
        help: String,
        sizeRange: IntRange = 1..Int.MAX_VALUE
    ) = positionalList(name, help = help, sizeRange = sizeRange) { this }

    /**
     * Creates a DelegateProvider for a sequence of positional arguments which returns a List containing the arguments.
     */
    fun positionalList(
        help: String,
        sizeRange: IntRange = 1..Int.MAX_VALUE
    ) = DelegateProvider { identifier ->
        positionalList(identifierToArgName(identifier), help = help, sizeRange = sizeRange)
    }

    /**
     * Creates a Delegate for a sequence of positional arguments which returns a List containing the transformed
     * arguments.
     */
    fun <T> positionalList(
        name: String,
        help: String,
        sizeRange: IntRange = 1..Int.MAX_VALUE,
        transform: String.() -> T
    ): Delegate<List<T>> {
        sizeRange.run {
            require(step == 1) { "step must be 1, not $step" }
            require(first <= last) { "backwards ranges are not allowed: $first > $last" }
            require(first >= 0) { "sizeRange cannot start at $first, must be non-negative" }

            // Technically, last == 0 is ok but not especially useful, so we
            // disallow it as it's probably unintentional.
            require(last > 0) { "sizeRange only allows $last arguments, must allow at least 1" }
        }

        return PositionalDelegate(this, name, sizeRange, help = help, transform = transform)
    }

    /**
     * Creates a DelegateProvider for a sequence of positional arguments which returns a List containing the transformed
     * arguments.
     */
    fun <T> positionalList(
        help: String,
        sizeRange: IntRange = 1..Int.MAX_VALUE,
        transform: String.() -> T
    ) = DelegateProvider { identifier -> positionalList(identifierToArgName(identifier), help, sizeRange, transform) }

    abstract class Delegate<out T> internal constructor() {
        /** The value associated with this delegate */
        abstract val value: T

        /** The name used to refer to this delegate's value in error messages */
        abstract val errorName: String

        /** The user-visible help text for this delegate */
        abstract val help: String

        /** Add validation logic. Validator should throw a [SystemExitException] on failure. */
        abstract fun addValidator(validator: Delegate<T>.() -> Unit): Delegate<T>

        /** Allows this object to act as a property delegate */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

        /**
         * Allows this object to act as a property delegate provider.
         *
         * It provides itself, and also registers itself with the [ArgParser] at that time.
         */
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): Delegate<T> {
            registerRoot()
            return this
        }

        internal abstract val parser: ArgParser

        /**
         * Indicates whether or not a value has been set for this delegate
         */
        internal abstract val hasValue: Boolean

        internal fun checkHasValue() {
            if (!hasValue) throw MissingValueException(errorName)
        }

        internal abstract fun validate()

        internal abstract fun toHelpFormatterValue(): HelpFormatter.Value

        internal fun registerRoot() {
            parser.checkNotParsed()
            parser.delegates.add(this)
            registerLeaf(this)
        }

        internal abstract fun registerLeaf(root: Delegate<*>)

        internal abstract val hasValidators: Boolean
    }

    /**
     * Provides a [Delegate] when given a name. This makes it possible to infer
     * a name for the `Delegate` based on the name it is bound to, rather than
     * specifying a name explicitly.
     */
    class DelegateProvider<out T>(
        private val default: (() -> T)? = null,
        internal val ctor: (identifier: String) -> Delegate<T>
    ) {
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): Delegate<T> {
            val delegate = ctor(prop.name)
            return (if (default == null) delegate
                    else delegate.default(default)).provideDelegate(thisRef, prop)
        }
    }

    /**
     * @property value a Holder containing the current value associated with this option, or null if unset
     * @property optionName the name used for this option in this invocation
     * @property arguments the arguments supplied for this option
     */
    data class OptionInvocation<T> internal constructor(
        // Internal constructor so future versions can add properties
        // without breaking compatibility.
        val value: Holder<T>?,
        val optionName: String,
        val arguments: List<String>
    )

    private val shortOptionDelegates = mutableMapOf<Char, OptionDelegate<*>>()
    private val longOptionDelegates = mutableMapOf<String, OptionDelegate<*>>()
    private val positionalDelegates = mutableListOf<Pair<PositionalDelegate<*>, Boolean>>()
    private val delegates = LinkedHashSet<Delegate<*>>()

    internal fun registerOption(name: String, delegate: OptionDelegate<*>) {
        if (name.startsWith("--")) {
            require(name.length > 2) { "long option '$name' must have at least one character after hyphen" }
            require(name !in longOptionDelegates) { "long option '$name' already in use" }
            longOptionDelegates[name] = delegate
        } else if (name.startsWith("-")) {
            require(name.length == 2) { "short option '$name' can only have one character after hyphen" }
            val key = name[1]
            require(key !in shortOptionDelegates) { "short option '$name' already in use" }
            shortOptionDelegates[key] = delegate
        } else {
            throw IllegalArgumentException("illegal option name '$name' -- must start with '-' or '--'")
        }
    }

    internal fun registerPositional(delegate: PositionalDelegate<*>, hasDefault: Boolean) {
        positionalDelegates.add(delegate to hasDefault)
    }

    private var inValidation = false
    private var finished = false

    /**
     * Ensures that arguments have been parsed and validated.
     *
     * @throws SystemExitException if parsing or validation failed.
     */
    fun force() {
        if (!inParse) {
            if (!finished) {
                parseOptions
                if (!inValidation) {
                    inValidation = true
                    try {
                        for (delegate in delegates) delegate.checkHasValue()
                        for (delegate in delegates) delegate.validate()
                    } finally {
                        inValidation = false
                    }
                }
            }
        }
    }

    /**
     * Provides an instance of T, where all arguments have already been parsed and validated
     */
    fun <T> parseInto(constructor: (ArgParser) -> T): T {
        if (builtinDelegateCount != delegates.size) {
            throw IllegalStateException("You can only use the parseInto function with a clean ArgParser instance")
        }
        val provided = constructor(this)
        force()
        return provided
    }

    private var inParse = false

    internal fun checkNotParsed() {
        if (inParse || finished) throw IllegalStateException("arguments have already been parsed")
    }

    private val parseOptions by lazy {
        val positionalArguments = mutableListOf<String>()
        inParse = true
        try {
            var i = 0
            optionLoop@ while (i < args.size) {
                val arg = args[i]
                i += when {
                    arg == "--" -> {
                        i++
                        break@optionLoop
                    }
                    arg.startsWith("--") ->
                        parseLongOpt(i, args, positionalArguments)
                    arg.startsWith("-") ->
                        parseShortOpts(i, args, positionalArguments)
                    else -> {
                        positionalArguments.add(arg)
                        when (mode) {
                            Mode.GNU -> 1
                            Mode.POSIX -> {
                                i++
                                break@optionLoop
                            }
                        }
                    }
                }
            }

            // Collect remaining arguments as positional-only arguments
            positionalArguments.addAll(args.slice(i until args.size))

            parsePositionalArguments(positionalArguments)
            finished = true
        } finally {
            inParse = false
        }
    }

    private fun minSizeOfPositionalArgs() = (positionalDelegates.map {
        if (it.second) 0 else it.first.sizeRange.first
    }.sum()).coerceAtLeast(0)

    private fun parsePositionalArguments(args: List<String>) {
        var lastValueName: String? = null
        var index = 0
        var remaining = args.size
        var extra = (remaining - positionalDelegates.map {
            if (it.second) 0 else it.first.sizeRange.first
        }.sum()).coerceAtLeast(0)
        for ((delegate, hasDefault) in positionalDelegates) {
            val minSize = if (hasDefault) 0 else delegate.sizeRange.first
            val sizeRange = delegate.sizeRange
            val chunkSize = (minSize + extra).coerceAtMost(sizeRange.last)
            if (chunkSize > remaining) {
                throw MissingRequiredPositionalArgumentException(delegate.errorName)
            }
            if (chunkSize != 0 || !hasDefault) {
                delegate.parseArguments(args.subList(index, index + chunkSize))
            }
            lastValueName = delegate.errorName
            index += chunkSize
            remaining -= chunkSize
            extra -= chunkSize - minSize
        }
        if (remaining > 0) {
            throw UnexpectedPositionalArgumentException(lastValueName)
        }
    }

    /**
     * @param index index into args, starting at a long option, eg: "--verbose"
     * @param args array of command-line arguments
     * @param consumedPositionalArgs consumed positional arguments for a measurement when eating undeclared args.
     * @return number of arguments that have been processed
     */
    private fun parseLongOpt(index: Int, args: Array<out String>, consumedPositionalArgs: List<String>): Int {
        val name: String
        val firstArg: String?
        val m = NAME_EQUALS_VALUE_REGEX.matchEntire(args[index])
        if (m == null) {
            name = args[index]
            firstArg = null
        } else {
            // if NAME_EQUALS_VALUE_REGEX then there must be groups 1 and 2
            name = m.groups[1]!!.value
            firstArg = m.groups[2]!!.value
        }
        val delegate = longOptionDelegates[name]
        return if (delegate == null) {
            if (!skippingUnrecognizedArgs) {
                throw UnrecognizedOptionException(name)
            } else {
                eatUnrecognizedArgs(firstArg, index, args, consumedPositionalArgs.size)
            }
        } else {
            var consumedArgs = delegate.parseOption(name, firstArg, index + 1, args)
            if (firstArg != null) {
                if (consumedArgs < 1) throw UnexpectedOptionArgumentException(name)
                consumedArgs -= 1
            }
            1 + consumedArgs
        }
    }



    /**
     * @param index index into args, starting at a set of short options, eg: "-abXv"
     * @param args array of command-line arguments
     * @param consumedPositionalArgs consumed positional arguments for a measurement when eating undeclared args.
     * @return number of arguments that have been processed
     */
    private fun parseShortOpts(index: Int, args: Array<out String>, consumedPositionalArgs: List<String>): Int {
        val opts = args[index]
        var optIndex = 1
        while (optIndex < opts.length) {
            val optKey = opts[optIndex]
            val optName = "-$optKey"
            optIndex++ // optIndex now points just after optKey

            val delegate = shortOptionDelegates[optKey]
            val firstArg = if (optIndex >= opts.length) null else opts.substring(optIndex)
            if (delegate == null) {
                if (!skippingUnrecognizedArgs) {
                    throw UnrecognizedOptionException(optName)
                } else {
                    return eatUnrecognizedArgs(firstArg, index, args, consumedPositionalArgs.size)
                }
            } else {
                val consumed = delegate.parseOption(optName, firstArg, index + 1, args)
                if (consumed > 0) {
                    return consumed + (if (firstArg == null) 1 else 0)
                }
            }
        }
        return 1
    }

    /**
     * Eat undeclared argName and potential value while [skippingUnrecognizedArgs] is true.
     *
     * In [Mode.POSIX], It will eat short or long opt arg name and one following value if exists.
     *
     * In [Mode.GNU] If will eat hort or long opt arg name and all following values if exists.
     *
     * @param firstArg value of current parsing ArgName, may be null.
     * @param index current parsing index (related to args)
     * @param args arg array to parse.
     * @param consumedPositionalArgCount counts of consumed positional arguments for a precious measurement when eating undeclared args in [Mode.GNU].
     * @return consumed args count.
     */
    private fun eatUnrecognizedArgs(firstArg: String?, index: Int, args: Array<out String>, consumedPositionalArgCount: Int): Int {
        /**
         * since we don't know if it's a flagging, storing or else.
         * we can only eat this unrecognized option and potential 'values'
         * and hope 'positional args' won't be affected.
         */
        if (firstArg != null) {
            // -oArgument, or --name=value, potential argument had been eaten as well, so move on.
            return 1
        }
        // out of range. stop.
        if (index + 1 !in args.indices) {
            return 1
        }

        var values = 0
        for (i in index + 1 until args.size) {
            if (args[i].startsWith("-")) {
                break
            }
            values++
        }
        val reachToEnd = values == (args.size - index - 1)
        val minSizeOfPositionalArgs = minSizeOfPositionalArgs()
        val remainingPositionalArgs = minSizeOfPositionalArgs - consumedPositionalArgCount
        if (remainingPositionalArgs <= 0) {
            // consuming all values blindly
            return 1 + values
        }
        if (reachToEnd) {
            // no one starts with '--' from [index+1]
            // keep potential positional args.
            // both GNU and POSIX can use this rule.
            return (1 + values - remainingPositionalArgs).coerceAtLeast(1)
        }
        // not reach to end, and still have remaining positional args.
        when (mode) {
            Mode.POSIX -> {
                // option exists, so consume args till next option.
                // because options must appear before positional args.
                return 1 + values
            }
            // options may appear after positional argument,
            // but here we actually don't know whether remaining positional args are skipped by [values] or not.
            // we should be more careful to take a precious consuming.
            Mode.GNU -> {
                // try our best to keep enough positional args.
                var minPositionalArgIndex = 0
                var meetPositionalArgs = 0
                var revIndex = args.size - 1
                var haveEnoughPositionalArgs = false
                while (revIndex > index) {
                    if (args[revIndex].startsWith("-")) {
                        revIndex--
                        continue
                    }
                    meetPositionalArgs++
                    minPositionalArgIndex = revIndex
                    if (meetPositionalArgs == remainingPositionalArgs) {
                        haveEnoughPositionalArgs = true
                        break
                    }
                    revIndex--
                }
                if (haveEnoughPositionalArgs) {
                    // the nearest positional arg is laid in [minPositionalArgIndex], so we drop [index,minPositionalArgIndex-1]
                    return (minPositionalArgIndex - index)
                }
                // not enough positional args, error must be thrown in [parsePositionalArguments].
                // here we just drop undeclared arg itself (act as a flagging arg).
                return 1
            }
        }
    }

    init {
        if (helpFormatter != null) {
            option<Unit>("-h", "--help",
                    errorName = "HELP", // This should never be used, but we need to say something
                    help = "show this help message and exit") {
                throw ShowHelpException(helpFormatter, delegates.toList())
            }.default(Unit).registerRoot()
        }
    }

    private val builtinDelegateCount = delegates.size
}

private val NAME_EQUALS_VALUE_REGEX = Regex("^([^=]+)=(.*)$")
internal val LEADING_HYPHENS_REGEX = Regex("^-{1,2}")

private const val OPTION_CHAR_CLASS = "[a-zA-Z0-9]"
internal val OPTION_NAME_RE = Regex("^(-$OPTION_CHAR_CLASS)|(--$OPTION_CHAR_CLASS+([-_\\.]$OPTION_CHAR_CLASS+)*)$")

private const val ARG_INITIAL_CHAR_CLASS = "[A-Z]"
private const val ARG_CHAR_CLASS = "[A-Z0-9]"
internal val ARG_NAME_RE = Regex("^$ARG_INITIAL_CHAR_CLASS+([-_\\.]$ARG_CHAR_CLASS+)*$")
