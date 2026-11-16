package com.argon.launcher.account

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Persists the account list + which one is active in a plain JSON file in app-private storage.
 * No external dependency, no database: this is simple enough to not need one.
 */
class AccountRepository(context: Context) {
    private val file = File(context.filesDir, "accounts.json")
    private val gson = Gson()

    private data class Store(val accounts: List<Account> = emptyList(), val selectedAccountId: String? = null)

    @Synchronized
    fun getAccounts(): List<Account> = readStore().accounts

    @Synchronized
    fun getSelectedAccount(): Account? {
        val store = readStore()
        return store.accounts.firstOrNull { it.id == store.selectedAccountId }
    }

    @Synchronized
    fun addOrUpdate(account: Account, selectIt: Boolean = true) {
        val store = readStore()
        val updated = store.accounts.filterNot { it.id == account.id } + account
        writeStore(Store(updated, if (selectIt) account.id else store.selectedAccountId))
    }

    @Synchronized
    fun remove(accountId: String) {
        val store = readStore()
        val updated = store.accounts.filterNot { it.id == accountId }
        val selected = if (store.selectedAccountId == accountId) updated.firstOrNull()?.id else store.selectedAccountId
        writeStore(Store(updated, selected))
    }

    @Synchronized
    fun select(accountId: String) {
        val store = readStore()
        writeStore(store.copy(selectedAccountId = accountId))
    }

    private fun readStore(): Store {
        if (!file.exists()) return Store()
        return try {
            gson.fromJson(file.readText(), Store::class.java) ?: Store()
        } catch (e: Exception) {
            Store()
        }
    }

    private fun writeStore(store: Store) {
        file.writeText(gson.toJson(store))
    }
}
