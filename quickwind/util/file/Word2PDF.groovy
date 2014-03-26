package quickwind.util.file

import com.jacob.activeX.*
import com.jacob.com.*

class Word2PDF {
    static boolean saveWordAsPDF(String filePath,String outFile){
        ComThread.InitSTA()
        def wordCom=new ActiveXComponent("Word.Application")
        try{
            Dispatch wrdDocs=Dispatch.get(wordCom,"Documents").toDispatch()
            Dispatch wordDoc=Dispatch.call(wrdDocs,"Open",filePath, new Variant(true),new Variant(false)).toDispatch()
            Dispatch.invoke(wordDoc,"ExportAsFixedFormat",Dispatch.Method,
                       [outFile,new Variant(17),new Variant(false),new Variant(0),new Variant(0),
                        new Variant(0),new Variant(0),new Variant(false),new Variant(true),
                        new Variant(0),new Variant(false),new Variant(true),new Variant(false)] as Object[],new int[0])

            return true
        }catch(Exception es){
            es.printStackTrace()
            return false
        }finally{
            if(wordCom!=null) {
                wordCom.invoke("Quit",[] as Variant[])
                wordCom=null
                ComThread.Release()
            }
        }
    }
}

