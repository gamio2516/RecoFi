package jp.knaka.cardmemo.storage
import android.content.Context
object StorageProvider { @Volatile private var db:RecoFiDatabase?=null; fun database(context:Context)=db?:synchronized(this){db?:RecoFiDatabase.open(context.applicationContext).also{db=it}}; internal fun resetForTest(){db?.close();db=null} }
