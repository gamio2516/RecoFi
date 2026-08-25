# RecoFi自動バックアップ

自動バックアップは、家計データを保存するRoomとは分離されています。保存先URI、ON/OFF、最終成功日時、権限状態だけをPreferences DataStoreへ保存し、これらの端末固有設定はBackup Format v5へ含めません。

## 保存の流れ

1. ユーザーがStorage Access Frameworkのフォルダ選択画面で保存先を選ぶ。
2. RecoFiがread/writeのpersistable URI permissionを保持する。
3. WorkManagerのunique periodic workが約24時間ごとに動作する。実行時刻はOSの省電力制御により前後する。
4. 現在のRoomデータをBackup Format v5で完全にencodeし、書き込み前にdecode検証する。
5. `RecoFi_auto_yyyy-MM-dd_HHmmss.json`を作成する。
6. 作成ファイルを再読込し、元の生成内容との全文一致とBackupCodecによる完全検証を確認する。
7. 検証成功後だけ最終成功日時を更新する。
8. RecoFi自動バックアップ名に一致するファイルだけを対象に、最新7世代を残す。

書き込み・再読込・検証の失敗は家計DBを変更しません。権限が失われた場合は自動バックアップをOFFにし、設定画面で保存先の再選択を案内します。

Google DriveなどのクラウドDocumentsProviderでは、同期遅延、オフライン、URIの移動・削除、delete非対応があり得ます。古いファイルを安全に削除できない場合は、新しい検証済みバックアップを成功として保持し、ローテーション警告を表示します。RecoFi以外のファイルは削除しません。
