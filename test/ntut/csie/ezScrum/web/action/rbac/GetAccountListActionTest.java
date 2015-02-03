package ntut.csie.ezScrum.web.action.rbac;

import java.io.File;
import java.io.IOException;
import java.util.List;

import ntut.csie.ezScrum.issue.sql.service.core.Configuration;
import ntut.csie.ezScrum.test.TestTool;
import ntut.csie.ezScrum.test.CreateData.CopyProject;
import ntut.csie.ezScrum.test.CreateData.CreateAccount;
import ntut.csie.ezScrum.test.CreateData.CreateProject;
import ntut.csie.ezScrum.test.CreateData.InitialSQL;
import ntut.csie.ezScrum.web.dataObject.AccountObject;
import ntut.csie.ezScrum.web.mapper.AccountMapper;
import ntut.csie.jcis.account.core.LogonException;
import servletunit.struts.MockStrutsTestCase;

// 一般使用者更新資料
public class GetAccountListActionTest extends MockStrutsTestCase {

	private CreateProject CP;
	private CreateAccount CA;
	private int ProjectCount = 1;
	private int AccountCount = 5;
	private String actionPath = "/getAccountList";	// defined in "struts-config.xml"

	private Configuration configuration;
	private AccountMapper accountMapper;

	public GetAccountListActionTest(String testMethod) {
		super(testMethod);
	}

	protected void setUp() throws Exception {
		configuration = new Configuration();
		configuration.setTestMode(true);
		configuration.save();
		
		InitialSQL ini = new InitialSQL(configuration);
		ini.exe();											// 初始化 SQL

		// 新增Project
		this.CP = new CreateProject(this.ProjectCount);
		this.CP.exeCreate();

		this.accountMapper = new AccountMapper();

		super.setUp();

		// 固定行為可抽離
		setContextDirectory(new File(configuration.getBaseDirPath() + "/WebContent"));		// 設定讀取的 struts-config 檔案路徑
		setServletConfigFile("/WEB-INF/struts-config.xml");
		setRequestPathInfo(this.actionPath);

		// ============= release ==============
		ini = null;
	}

	protected void tearDown() throws IOException, Exception {
		InitialSQL ini = new InitialSQL(configuration);
		ini.exe();											// 初始化 SQL

		CopyProject copyProject = new CopyProject(this.CP);
		copyProject.exeDelete_Project();					// 刪除測試檔案

		configuration.setTestMode(false);
		configuration.save();
		
		super.tearDown();

		// ============= release ==============
		ini = null;
		copyProject = null;
		this.CP = null;
		this.CA = null;
		this.config = null;
		this.accountMapper = null;
		configuration = null;
	}

	// One
	public void testGetAccountListAction() throws LogonException {
		// 新增使用者
		this.CA = new CreateAccount(1);
		this.CA.exe();

		// ================ set initial data =======================
		String projectId = this.CP.getProjectList().get(0).getName();

		String userId = this.CA.getAccount_ID(1);
		String userPw = this.CA.getAccount_PWD(1);
		String userMail = this.CA.getAccount_Mail(1);
		String userName = this.CA.getAccount_RealName(1);
		String userEnable = "true";	// default is true

		// ================ set session info ========================
		request.getSession().setAttribute("UserSession", configuration.getUserSession());

		// ================ set URL parameter ========================
		request.setHeader("Referer", "?PID=" + projectId);	// SessionManager 會對URL的參數作分析 ,未帶入此參數無法存入session

		// 執行 action
		actionPerform();

		/*
		 * Verify:
		 */
		List<AccountObject> accountList = this.accountMapper.getAccounts();
		assertEquals(2, accountList.size());	// + admin

		AccountObject account = this.accountMapper.getAccount(userId);

		assertNotNull(account);
		assertEquals(account.getUsername(), userId);
		assertEquals(account.getPassword(), (new TestTool()).getMd5(userPw));
		assertEquals(account.getEmail(), userMail);
		assertEquals(account.getNickName(), userName);
		assertEquals(account.getEnable(), userEnable);
	}

	// multiple
	public void testGetAccountListActionX() throws LogonException {
		// 新增使用者
		this.CA = new CreateAccount(this.AccountCount);
		this.CA.exe();

		// ================ set initial data =======================
		String projectId = this.CP.getProjectList().get(0).getName();

		// ================ set session info ========================
		request.getSession().setAttribute("UserSession", configuration.getUserSession());

		// ================ set URL parameter ========================
		request.setHeader("Referer", "?PID=" + projectId);	// SessionManager 會對URL的參數作分析 ,未帶入此參數無法存入session

		// 執行 action
		actionPerform();

		/*
		 * Verify:
		 */
		List<AccountObject> accountList = this.accountMapper.getAccounts();

		assertEquals(this.AccountCount + 1, accountList.size());	// + admin

		for (int i = 1; i < this.AccountCount + 1; i++) {
			AccountObject account = this.accountMapper.getAccount(CA.getAccount_ID(i));

			assertNotNull(account);
			assertEquals(account.getUsername(), this.CA.getAccount_ID(i));
			assertEquals(account.getPassword(), (new TestTool()).getMd5(this.CA.getAccount_PWD(i)));
			assertEquals(account.getEmail(), this.CA.getAccount_Mail(i));
			assertEquals(account.getNickName(), this.CA.getAccount_RealName(i));
			assertEquals(account.getEnable(), "true");
		}

	}

}
