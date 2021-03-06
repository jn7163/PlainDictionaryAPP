package com.knziha.plod.dictionarymodels;

import androidx.annotation.NonNull;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import com.knziha.plod.PlainDict.CMN;
import com.knziha.plod.PlainDict.MainActivityUIBase;
import com.knziha.plod.dictionary.Utils.BU;
import com.knziha.plod.dictionary.Utils.Flag;
import com.knziha.plod.dictionary.Utils.IU;
import com.knziha.plod.dictionary.Utils.LinkastReUsageHashMap;
import com.knziha.plod.dictionary.Utils.ReusableByteOutputStream;
import com.knziha.plod.dictionary.Utils.SU;
import com.knziha.plod.dictionarymanager.files.CachedDirectory;
import com.knziha.rbtree.RBTree_additive;

import org.apache.commons.text.StringEscapeUtils;
import org.joni.Option;
import org.joni.Regex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.knziha.plod.dictionarymodels.mdict_transient.goodNull;

/*
 Mdict point to local txt file.
 date:2019.11.28
 author:KnIfER
*/
public class mdict_txt extends mdict {
	static Pattern NumberPattern = Pattern.compile("[0-9]+");
	private long file_length;
	byte[] lineBreakText;
	final static int block_size = 4096;
	int mBlockSize = block_size;
	final static int DefaultCacheItemCount = 1024*1024/4096;
	int mCacheItemCount;
	public static final Pattern chapterReg = Pattern.compile("(第[一二三四五六七八九十]{1,10}章)");
	class TextBlock{
		int blockIndex;
		int breakIndex=-1;
		int blockSize;
		byte[] data = new byte[mBlockSize];
		@NonNull
		@Override
		public String toString() {
			return "#"+blockIndex+":"+breakIndex+"/"+blockSize+"≈"+new String(data, 0, 100, _charset);
		}
	}
	LinkastReUsageHashMap<Integer, TextBlock> block_cache;
	TextBlock[] textScope = new TextBlock[2];
	/**
	 * 0 0
	 * | 1 1
	 * | | 2 2
	 * | | | 3 3
	 */
	@Override
	public String getRecordAt(int position) throws IOException {
		if(editingState && getContentEditable()){
			CachedDirectory cf = getInternalResourcePath(false);
			boolean ce =  cf.cachedExists();
			File p = ce?new File(cf, Integer.toString(position)):null;
			boolean pExists = ce && p.exists();
			//retrieve page from database
			if(getSavePageToDatabase()){
				String url=Integer.toString(position);
				getCon(true).enssurePageTable();
				con.preparePageContain();
				if(con.containsPage(url)){
					if(!pExists) return con.getPageString(url, StandardCharsets.UTF_8);
					else{
						Object[] results=con.getPageAndTime(url);
						if(results!=null) {
							/* 删除文件，除非文件最近修改过。 */
							/* 只在此处删除文件。 */
							/* xian baocun dao shujuku ->  baocun dao wenjian  */
							/* --> canliu laojiu de shuju. Duqu shi, bu yu li hui.*/
							/* xian baocun dao wenjian -> baocun dao shujuku  */
							/* --> canliu laojiu de wenjian. Duqu shi shanchu */
							if ((long) results[1] > p.lastModified()) {
								p.delete();
								return new String((byte[])results[0]);
							}
						}
					}
				}
			}
			//retrieve page from file system
			if(pExists){
				return BU.fileToString(p);
			}
		}
		int centerBlock = position;
		FileInputStream fin = new FileInputStream(f);
		TextBlock tmpBlock;
		int toSkip;
		textScope[0] = block_cache.get(centerBlock);
		if(textScope[0]==null){
			tmpBlock = new TextBlock();
			tmpBlock.blockIndex = centerBlock;
			toSkip = mBlockSize *tmpBlock.blockIndex;
			if(toSkip>0)
				fin.skip(toSkip);
			tmpBlock.blockSize=fin.read(tmpBlock.data);
			textScope[0]=tmpBlock;
		}

		textScope[1] = block_cache.get(centerBlock+1);
		if(textScope[1]==null && centerBlock+1<_num_entries){
			tmpBlock = new TextBlock();
			tmpBlock.blockIndex = centerBlock+1;
			toSkip = mBlockSize - textScope[0].blockSize;
			if(toSkip>0)
				fin.skip(toSkip);
			tmpBlock.blockSize=fin.read(tmpBlock.data);
			textScope[1]=tmpBlock;
		}

		ReusableByteOutputStream bos = new ReusableByteOutputStream(mBlockSize *2);
		bos.reset();
		tmpBlock = textScope[0];
		if(tmpBlock!=null){
			if(tmpBlock.blockIndex==0)
				tmpBlock.breakIndex=0;
			if(tmpBlock.breakIndex==-1)
				tmpBlock.breakIndex=mdict.indexOf(tmpBlock.data, 0, tmpBlock.blockSize, lineBreakText, 0, lineBreakText.length, 0);
			if(tmpBlock.breakIndex==-1)
				tmpBlock.breakIndex=0;
			bos.write(tmpBlock.data, tmpBlock.breakIndex, tmpBlock.blockSize-tmpBlock.breakIndex);
		}
		tmpBlock = textScope[1];
		if(tmpBlock!=null){
			if(tmpBlock.breakIndex==-1)
				tmpBlock.breakIndex=mdict.indexOf(tmpBlock.data, 0, tmpBlock.blockSize, lineBreakText, 0, lineBreakText.length, 0);
			if(tmpBlock.breakIndex>0)
				bos.write(tmpBlock.data, 0, tmpBlock.breakIndex);
		}

		for (TextBlock tI:textScope)
			if(tI!=null)
				block_cache.put(tI.blockIndex, tI);

		String val = new String(bos.getBytes(), 0, bos.size(), _charset);

		//CMN.Log(val);
		//CMN.Log(textScope[0].breakIndex, textScope[1].blockIndex,  textScope[1].blockSize, mdict.indexOf(tmpBlock.data, 0, tmpBlock.blockSize, lineBreakText, 0, lineBreakText.length, 0));

		val = StringEscapeUtils.escapeHtml3(val.trim());

		val = chapterReg.matcher(val).replaceAll("<h1>$1</h1>");

		return val.replace("\n", "<br/>");
	}

	//构造
	public mdict_txt(String fn, MainActivityUIBase _a) throws IOException {
		super(goodNull(fn), _a);
		a=_a;
		opt=a.opt;
		_Dictionary_fName=new File(fn).getName();
		_Dictionary_fName_Internal = fn.startsWith(opt.lastMdlibPath)?fn.substring(opt.lastMdlibPath.length()):fn;
		_Dictionary_fName_Internal = _Dictionary_fName_Internal.replace("/", ".");

		justifyInternal("."+_Dictionary_fName);

		mBlockSize = 1*block_size;

		htmlBuilder=new StringBuilder(htmlBase);
		htmlBuilder.append(js);
		htmlBaseLen=htmlBuilder.length();

		readInConfigs();

		//chatsetDec cd = new chatsetDec();
		//Toast.makeText(this,cd.guessFileEncoding(subscript_file),Toast.LENGTH_SHORT).show();
		//charset = isPlayingAsset?"utf8":cd.guessFileEncoding(subscript_file).split(",")[0];
		//if(charset.startsWith("windows"))
		//    charset = "UTF-16LE";

		block_cache = new LinkastReUsageHashMap<>(mCacheItemCount);

		FileInputStream fis = new FileInputStream(f);
		TextBlock tmpBlock=new TextBlock();
		tmpBlock.blockSize = fis.read(tmpBlock.data);

		block_cache.put(0, tmpBlock);

		CharsetDetector detector = new CharsetDetector();
		detector.setText(tmpBlock.data);
		CharsetMatch match = detector.detect();
		String charset = "utf8";
		if(match!=null && match.getConfidence()>=75)
			charset = match.getName();
		//CMN.Log("检测结果：", charset);
		_charset = Charset.forName(charset);
		lineBreakText = "\n".getBytes(_charset);
		file_length = f.length();
		_num_record_blocks=
		_num_entries = (long) Math.ceil(file_length*1.f/ mBlockSize);
		if(bgColor==null)
			bgColor=CMN.GlobalPageBackground;

		mCacheItemCount = DefaultCacheItemCount;
	}

	@Override
	protected void initLogically() {
		_num_record_blocks=-1;
		String fn = (String) SU.UniversalObject;
		fn = new File(fn).getAbsolutePath();
		f = new File(fn);
		_Dictionary_fName = f.getName();
	}

	@Override
	protected void onPageSaved() {
		super.onPageSaved();
		a.notifyDictionaryDatabaseChanged(mdict_txt.this);
	}

	@Override
	protected String getSaveNameForUrl(String finalUrl) {
		boolean needTrim=!finalUrl.contains(".php");//动态资源需要保留参数
		int start = 0;
		int end = needTrim?finalUrl.indexOf("?"):finalUrl.length();
		if(end<0) end=finalUrl.length();
		String name=finalUrl.substring(start, end);
		try {
			name=URLDecoder.decode(name, "utf8");
		} catch (UnsupportedEncodingException ignored) { }
		if(!needTrim) name=name.replaceAll("[=?&|:*<>]", "_");
		if(name.length()>64){
			name=name.substring(0, 56)+"_"+name.length()+"_"+name.hashCode();
		}
		return name;
	}

	@Override
	String getSimplestInjection() {
		return js;
	}

	@Override
	public String getEntryAt(int position) {
		return Integer.toString(position);
	}

	public boolean hasCover() {
		return false;
	}

	@Override
	public String getLexicalEntryAt(int position) {
		return "第"+position+"页";
	}

	@Override
	public String getEntryAt(int position, Flag mflag) {
		return getEntryAt(position);
	}

	@Override
	public int lookUp(String keyword, boolean isSrict) {
		if(NumberPattern.matcher(keyword).matches()){
			int idx = IU.parsint(keyword);
			if(idx>0 && idx<_num_entries)
				return idx;
		}
		return -1;
	}

	@Override
	public InputStream getResourceByKey(String key) {
		return null;
	}

	@Override
	public boolean hasMdd() {
		return false;
	}

	@Override
	protected InputStream mOpenInputStream() throws FileNotFoundException {
		return new FileInputStream(f);
	}

	@Override
	protected boolean StreamAvailable() {
		return false;
	}

	@Override
	public void size_confined_lookUp5(String keyword, RBTree_additive combining_search_tree, int SelfAtIdx, int theta) {
		//searchKeys.remove(keyword);
		//searchKeys.add(0, keyword);
	}

	@Override
	public void flowerFindAllContents(String key, int selfAtIdx, AbsAdvancedSearchLogicLayer SearchLauncher) throws IOException {
		//SU.Log("Find In All Contents Stated");
		byte[][][] matcher=null;
		Regex Joniregex = null;
		if(getUseJoniRegex(1)){
			if(encoding==null) bakeJoniEncoding();
			if(encoding!=null) {
				//if (getRegexAutoAddHead() && !key.startsWith(".*"))
				//	key = ".*" + key;
				byte[] pattern = key.getBytes(_charset);
				Joniregex = new Regex(pattern, 0, pattern.length, getRegexOption(), encoding);
			}
		}
		if(Joniregex==null){
			String keyword = key.toLowerCase();
			String upperKey = keyword.toUpperCase();
			matcher = new byte[upperKey.equals(keyword)?1:2][][];
			matcher[0] = flowerSanLieZhi(keyword);
			if(matcher.length==2)
				matcher[1] = flowerSanLieZhi(upperKey);
		}

		split_recs_thread_number = _num_record_blocks<6?1:(int) (_num_record_blocks/6);//Runtime.getRuntime().availableProcessors()/2*2+10;
		split_recs_thread_number = split_recs_thread_number>16?6:split_recs_thread_number;
		final int thread_number = Math.min(Runtime.getRuntime().availableProcessors()/2*2+2, split_recs_thread_number);
		//SU.Log("fatal_","split_recs_thread_number", split_recs_thread_number);
		//SU.Log("fatal_","thread_number", thread_number);

		final int step = (int) (_num_record_blocks/split_recs_thread_number);
		final int yuShu=(int) (_num_record_blocks%split_recs_thread_number);

		ArrayList<Integer>[] _combining_search_tree=SearchLauncher.getCombinedTree(selfAtIdx);
		boolean hold=false;
		if(SearchLauncher.combining_search_tree==null){
			hold=true; _combining_search_tree=combining_search_tree_4;
		}
		if(_combining_search_tree==null || _combining_search_tree.length!=split_recs_thread_number){
			_combining_search_tree = new ArrayList[split_recs_thread_number];
			if(hold)
				combining_search_tree_4=_combining_search_tree;
			else
				SearchLauncher.setCombinedTree(selfAtIdx, _combining_search_tree);
		}

		SearchLauncher.poolEUSize.set(SearchLauncher.dirtyProgressCounter=0);

		//ArrayList<Thread> fixedThreadPool = new ArrayList<>(thread_number);
		ExecutorService fixedThreadPool = OpenThreadPool(thread_number);
		for(int ti=0; ti<split_recs_thread_number; ti++){//分  thread_number 股线程运行
			//SU.Log("执行", ti , split_recs_thread_number);
			if(SearchLauncher.IsInterrupted || searchCancled) break;
			final int it = ti;
			if(split_recs_thread_number>thread_number) while (SearchLauncher.poolEUSize.get()>=thread_number) {
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if(combining_search_tree_4[it]==null)
				combining_search_tree_4[it] = new ArrayList<>();

			if(split_recs_thread_number>thread_number) SearchLauncher.poolEUSize.addAndGet(1);

			Regex finalJoniregex = Joniregex;
			byte[][][] finalMatcher = matcher;
			//Thread t;
			//fixedThreadPool.add(t=new Thread(
			fixedThreadPool.execute(
					//(
					new Runnable(){@Override public void run()
					{
						if(SearchLauncher.IsInterrupted || searchCancled) { SearchLauncher.poolEUSize.set(0); return; }
						final ReusableByteOutputStream bos = new ReusableByteOutputStream(mBlockSize *2);//!!!避免反复申请内存
						try
						{
							InputStream data_in = mOpenInputStream();
							long seekTarget=it*step* mBlockSize;
							data_in.skip(seekTarget);
							int jiaX=0;
							if(it==split_recs_thread_number-1) jiaX=yuShu;
							TextBlock lastBlock=null;
							for(int i=it*step; i<it*step+step+jiaX; i++)//_num_record_blocks
							{
								if(SearchLauncher.IsInterrupted || searchCancled) { SearchLauncher.poolEUSize.set(0); return; }
								bos.reset();

								if(i>=_num_record_blocks) return;

								if(_num_record_blocks>1 && i==_num_record_blocks-1)
									continue;
								//todo 复用优化
								TextBlock tmpBlockA = lastBlock;
								if(tmpBlockA==null){
									tmpBlockA = new TextBlock();
									tmpBlockA.blockIndex = i;
									tmpBlockA.blockSize=data_in.read(tmpBlockA.data);
									if(tmpBlockA.blockSize>0) {
										seekTarget += tmpBlockA.blockSize;
										if (tmpBlockA.blockIndex == 0) tmpBlockA.breakIndex = 0;
										if (tmpBlockA.breakIndex == -1)
											tmpBlockA.breakIndex = mdict.indexOf(tmpBlockA.data, 0, tmpBlockA.blockSize, lineBreakText, 0, lineBreakText.length, 0);
										if (tmpBlockA.breakIndex == -1) tmpBlockA.breakIndex = 0;
									}
								}
								bos.write(tmpBlockA.data, tmpBlockA.breakIndex, tmpBlockA.blockSize-tmpBlockA.breakIndex);

								TextBlock tmpBlockB = new TextBlock();
								if(i+1<_num_record_blocks){
									tmpBlockB.blockIndex = i+1;
									tmpBlockB.blockSize=data_in.read(tmpBlockB.data);
									if(tmpBlockB.blockSize>0) {
										seekTarget += tmpBlockB.blockSize;
										if (tmpBlockB.breakIndex == -1)
											tmpBlockB.breakIndex = mdict.indexOf(tmpBlockB.data, 0, tmpBlockB.blockSize, lineBreakText, 0, lineBreakText.length, 0);
										if (tmpBlockA.breakIndex == -1) tmpBlockA.breakIndex = 0;
										if (tmpBlockB.breakIndex > 0)
											bos.write(tmpBlockB.data, 0, tmpBlockB.breakIndex);
										lastBlock = tmpBlockB;
									}
								}

								byte[] record_block_ = bos.getBytes();
								int recordodKeyLen = bos.size();
								//内容块读取完毕

								org.joni.Matcher Jonimatcher = null;
								if(finalJoniregex !=null)
									Jonimatcher = finalJoniregex.matcher(record_block_);
								if(SearchLauncher.IsInterrupted  || searchCancled ) break;

								int try_idx=Jonimatcher==null?
										flowerIndexOf(record_block_,0,recordodKeyLen, finalMatcher,0,0)
										:Jonimatcher.searchInterruptible(0, recordodKeyLen, Option.DEFAULT)
								;
								//SU.Log(try_idx, record_block_.length, recordodKeyLen);
								if(try_idx!=-1) {
									SearchLauncher.dirtyResultCounter++;
									combining_search_tree_4[it].add(i);
								}
								SearchLauncher.dirtyProgressCounter++;
							}
							data_in.close();

						} catch (Exception e) {
							CMN.Log(e);
						}
						SearchLauncher.thread_number_count--;
						if(split_recs_thread_number>thread_number) SearchLauncher.poolEUSize.addAndGet(-1);
					}}
			);
		}
		SearchLauncher.currentThreads=fixedThreadPool;
		fixedThreadPool.shutdown();
		try {
			fixedThreadPool.awaitTermination(5, TimeUnit.MINUTES);
		} catch (Exception e1) {
			SU.Log("Find In Full Text Interrupted!!!");
			//e1.printStackTrace();
		}
	}

	@Override
	public void flowerFindAllKeys(String key, int SelfAtIdx, AbsAdvancedSearchLogicLayer SearchLauncher) {
	}

	@Override
	public String getAboutString() {
		return _Dictionary_fName+"<br>"+f.getAbsolutePath()+"<br>"+f.lastModified()+"<br>"+_charset+"<br>"+mp4meta.utils.CMN.formatSize(f.length());
	}

	@Override
	public String getDictInfo() {
		return getAboutString();
	}
}
