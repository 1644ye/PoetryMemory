package org.poetrymemory.poetry

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.google.gson.*
import org.poetrymemory.core.*
import org.poetrymemory.remote.*

class DataActivity:PoetryActivity(){
    private var output=""
    private var restoring=false
    override fun onCreate(state:Bundle?){super.onCreate(state);output=state?.getString("output").orEmpty();restoring=state?.getBoolean("restoring")?:false;home()}
    override fun onSaveInstanceState(out:Bundle){out.putString("output",output);out.putBoolean("restoring",restoring);super.onSaveInstanceState(out)}
    private fun home(){
        page("数据与诗库",{finish()})
        panel.addView(ui.section("添加内容"))
        panel.addView(ui.item("导入诗集","从本机选择 ebook-library JSON","入"){restoring=false;pick()})
        panel.addView(ui.item("GitHub 诗库","账号、诗库下载与阅读进度同步","云"){open(RemoteActivity::class.java)})
        panel.addView(ui.item("诗库更新策略","手动检查、后台更新与网络偏好","新"){updateSettings()})
        panel.addView(ui.item("诗集模板与示例","保存标准 JSON，填写自己的诗文","样"){choose("选择模板",listOf("诗集模板","诗歌示例")){i->val name=listOf("poetry-template.json","poetry-example.json")[i];output=javaClass.classLoader!!.getResourceAsStream("protocol/$name")!!.bufferedReader().use{it.readText()};write(name)}})
        panel.addView(ui.section("保管数据"))
        panel.addView(ui.item("导出诗文","只导出有效正文，不含学习记录","出"){work({poems.effectiveExport().toString()}){output=it;write("poetry-content.json")}})
        panel.addView(ui.item("备份与恢复","包含草稿、覆盖及学习历史","存"){backups()})
    }
    private fun backups(){page("备份与恢复",{home()});panel.addView(ui.item("保存完整备份","正文、诗集关系、设置、草稿和学习历史"){work({store.backup().toString()}){output=it;write("poetry-backup.json")}});panel.addView(ui.item("从备份恢复","整体替换前先保存保护备份"){restoring=true;pick()})}
    private fun pick(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),10)}
    private fun write(name:String){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name),11)}
    @Deprecated("platform") override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(result!=Activity.RESULT_OK)return;val uri=data?.data?:return
        if(request==11){val text=output;work({contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use{it.write(text)}}){message("文件已保存")};return}
        if(request==10)work({val input=contentResolver.openInputStream(uri)!!.use{stream->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=Protocol.MAX_FILE_BYTES){"文件超过 32 MiB"};out.write(buffer,0,n)};out.toString("UTF-8")};if(restoring)JsonParser.parseString(input).asJsonObject else {val parsed=Protocol.parse(input,"导入文件");require(parsed.valid){parsed.errors.joinToString("\n"){it.message}};parsed.document!!}}){doc->
            if(restoring){val errors=store.validateBackup(doc);if(errors.isNotEmpty()){message(errors.joinToString("\n"));return@work};ui.dialog().setTitle("整体替换本机数据").setMessage("将恢复正文、关系、设置、草稿及学习历史；恢复前保存保护备份。GitHub 登录凭据不导入，恢复后自动更新与同步暂停。").setNegativeButton("取消",null).setPositiveButton("恢复"){_,_->work({store.restoreBackup(doc);getSharedPreferences("poetry-source-update",0).edit().putBoolean("check",false).putBoolean("download",false).commit();SourceUpdateJob.schedule(this)}){message("恢复完成；保护备份保存在应用私有目录")}}.show()}
            else {val original=doc.getAsJsonArray("books");val selected=original.filter{it.asJsonObject.s("type")=="poetry"};if(selected.isEmpty()){message("背诗只导入诗集，请选择含诗文的 JSON 文件");return@work};val books=JsonArray().apply{selected.forEach{add(it)}};doc.add("books",books);ui.dialog().setTitle("导入预览").setMessage("${books.size()} 本\n"+books.joinToString("\n"){it.asJsonObject.s("title")}+"\n同 ID 内容将更新，学习记录及本地覆盖保留。").setNegativeButton("取消",null).setPositiveButton("确认导入"){_,_->work({store.importFile(doc,books.associate{it.asJsonObject.s("id") to "update"})}){message("导入完成")}}.show()}
        }
    }
    private fun updateSettings(){val p=getSharedPreferences("poetry-source-update",0);page("诗库更新策略",{home()});listOf("check" to "每天自动检查","download" to "自动更新已下载内容","wifi" to "仅 Wi-Fi").forEach{(k,title)->panel.addView(ui.item("$title：${p.getBoolean(k,k=="wifi")}"){p.edit().putBoolean(k,!p.getBoolean(k,k=="wifi")).commit();SourceUpdateJob.schedule(this);updateSettings()})};panel.addView(ui.text("最近结果：${p.getString("result","尚未执行")}"));panel.addView(ui.item("立即检查更新"){work({SourceUpdateJob.check(this,false)}){message(it);updateSettings()}});panel.addView(ui.item("检查并更新已下载内容"){work({SourceUpdateJob.check(this,true)}){message(it);updateSettings()}});panel.addView(ui.text("只更新当前仓库已下载的诗集，不自动下载新书。本地编辑覆盖保留；学习使用开始时的正文快照。"))}
}
