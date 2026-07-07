package no.nav.syfo.utenlandsopphold.infrastructure.database

import java.sql.ResultSet

fun <T> ResultSet.toList(mapper: ResultSet.() -> T): List<T> =
    mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }
