package jp.knaka.cardmemo
import android.content.Context
import jp.knaka.cardmemo.storage.*
import kotlinx.coroutines.runBlocking
import java.io.*
import java.time.Instant
class BackupManager(private val context:Context){private val db get()=StorageProvider.database(context);private val appVersion get()=context.packageManager.getPackageInfo(context.packageName,0).versionName?:"unknown";fun createValidatedPayload():String=BackupCodec.encode(db,appVersion).also(BackupCodec::decodeAndValidate);fun writeBackup(out:OutputStream){out.bufferedWriter().use{it.write(createValidatedPayload())}};fun readAndValidate(text:String)=BackupCodec.decodeAndValidate(text);fun restore(backup:ValidatedBackup):File{val safety=writeSafetyBackup();runBlocking{RoomBackupAdapter.replace(db,backup.snapshot)};return safety};private fun writeSafetyBackup():File{val dir=File(context.filesDir,"restore-safety-backups");check((dir.exists()||dir.mkdirs())&&dir.isDirectory);val target=File(dir,"RecoFi_before_restore_${Instant.now().toString().replace(':','-')}.json");val temp=File(dir,".${target.name}.tmp");FileOutputStream(temp).use{it.write(createValidatedPayload().toByteArray());it.fd.sync()};check(temp.renameTo(target));BackupCodec.decodeAndValidate(target.readText());return target}}
