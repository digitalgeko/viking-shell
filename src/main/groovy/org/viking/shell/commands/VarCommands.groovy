package org.viking.shell.commands

import org.springframework.shell.core.CommandMarker
import org.springframework.shell.core.annotation.CliCommand
import org.springframework.shell.core.annotation.CliOption
import org.springframework.stereotype.Component

/**
 * Created by juanitoramonster on 4/10/14.
 */
@Component
class VarCommands implements CommandMarker {

    def variables = [:]

    @CliCommand(value = "var set", help = "Set a variable in the shell's context.")
    def set(
            @CliOption(
                key = "name",
                mandatory = true,
                help = "The name of the variable to set."
            ) String name,
            @CliOption(
                    key = "value",
                    mandatory = false,
                    help = "The value of the variable to set."
            ) String value,
            @CliOption(
                    key = "context",
                    mandatory = false,
                    help = "The context where the variable will be stored."
            ) String context
    ) {
       // TODO: Add logic to remove variable if value is null.

       // TODo: Add logic to verify if variable has a confilct name with a context

       if (context) {
           if (variables[context]) {
               variables[context].put(name, value)
           } else {
               def ctxMap = [:]
               ctxMap.put(name,value)
               variables.put(context, ctxMap)
           }
       } else {
           variables.put(name, value)
       }
       "Setting: ${context ? context : ""} $name = $value"
    }

    @CliCommand(value = "var get", help = "Get a variable in the shell's context.")
    def get(
            @CliOption(
                key = "name",
                mandatory = true,
                help = "The name of the variable to get."
            ) String name,
            @CliOption(
                key = "context",
                mandatory = false,
                help = "The context from where the variable will be retrieved."
            ) String context
    ) {
        def value = variables[name]
        if (context) {
            value =  variables[context][name]
        }
        return value
    }

    @CliCommand(value = "var show", help = "List the variables defined in a context.")
    def list(
            @CliOption(
                key = "context",
                mandatory = false,
                help = "The context from which the variables will be listed"
            ) String context
    ) {
        def varList = context ? "$context:\n" : "Globally variables defined:\n"
        if (context) {
            variables.each { key, value ->
                if (context ==  key) {
                    value.eachWithIndex { k, v, i ->
                        varList += "(${i + 1}) $k = $v\n"
                    }
                }
            }
        } else {
            variables.each { key, value ->
                if (! (value instanceof Map)) {
                    varList += "$key = $value\n"
                }
            }
        }
        return varList
    }

    @CliCommand(value = "var list-all", help = "List all the defined variables.")
    def listAll() {
        def varList = "Globally defined variables:\n"
        variables.each { key, value ->
            if (! (value instanceof Map)) {
                varList += "$key = $value\n"
                varList += "\n"
            }
        }

        variables.each { key, value ->
            if (value instanceof Map) {
                varList += "Varables defined in $key:\n"
                value.each { k,v ->
                    varList += "$k = $v\n"
                }
                varList += "\n"
            }
        }

        return varList
    }

    @CliCommand(value = "var list-contexts", help = "List all the defined contexts.")
    def listContexts() {
        def varList = "Contexts:\n"
        variables.each { key, value ->
            if (value instanceof Map) {
                varList += "$key\n"
            }
        }
        return varList
    }
}
