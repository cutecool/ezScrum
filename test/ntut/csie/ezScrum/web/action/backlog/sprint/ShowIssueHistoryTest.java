package ntut.csie.ezScrum.web.action.backlog.sprint;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ntut.csie.ezScrum.issue.core.IIssue;
import ntut.csie.ezScrum.issue.sql.service.core.Configuration;
import ntut.csie.ezScrum.refactoring.manager.ProjectManager;
import ntut.csie.ezScrum.test.CreateData.AddStoryToSprint;
import ntut.csie.ezScrum.test.CreateData.AddTaskToStory;
import ntut.csie.ezScrum.test.CreateData.AddUserToRole;
import ntut.csie.ezScrum.test.CreateData.CreateAccount;
import ntut.csie.ezScrum.test.CreateData.CreateProductBacklog;
import ntut.csie.ezScrum.test.CreateData.CreateProject;
import ntut.csie.ezScrum.test.CreateData.CreateSprint;
import ntut.csie.ezScrum.test.CreateData.CreateUnplannedItem;
import ntut.csie.ezScrum.test.CreateData.DropTask;
import ntut.csie.ezScrum.test.CreateData.EditUnplannedItem;
import ntut.csie.ezScrum.test.CreateData.InitialSQL;
import ntut.csie.ezScrum.web.helper.SprintBacklogHelper;
import ntut.csie.ezScrum.web.logic.SprintBacklogLogic;
import ntut.csie.jcis.resource.core.IProject;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import servletunit.struts.MockStrutsTestCase;

public class ShowIssueHistoryTest extends MockStrutsTestCase {
	private CreateProject mCreateProject;
	private CreateSprint mCreateSprint;
	private Configuration mConfig;
	private final String ACTION_PATH = "/showIssueHistory";
	private IProject mProject;
	private CreateUnplannedItem mCreateUnplanned;

	public ShowIssueHistoryTest(String testName) {
		super(testName);
	}

	protected void setUp() throws Exception {
		mConfig = new Configuration();
		mConfig.setTestMode(true);
		mConfig.store();

		// 刪除資料庫
		InitialSQL ini = new InitialSQL(mConfig);
		ini.exe();

		// 新增1個 project
		mCreateProject = new CreateProject(1);
		mCreateProject.exeCreate();
		mProject = mCreateProject.getProjectList().get(0);

		// 新增1個 sprint
		mCreateSprint = new CreateSprint(2, mCreateProject);
		mCreateSprint.exe();

		super.setUp();
		// ================ set action info ========================
		setContextDirectory(new File(mConfig.getBaseDirPath() + "/WebContent"));
		setServletConfigFile("/WEB-INF/struts-config.xml");
		setRequestPathInfo(ACTION_PATH);

		ini = null;
	}

	protected void tearDown() throws Exception {
		// 刪除資料庫
		InitialSQL ini = new InitialSQL(mConfig);
		ini.exe();

		ProjectManager projectManager = new ProjectManager();
		projectManager.deleteAllProject();
		projectManager.initialRoleBase(mConfig.getDataPath());

		mConfig.setTestMode(false);
		mConfig.store();

		super.tearDown();

		ini = null;
		projectManager = null;
		mCreateProject = null;
		mConfig = null;
	}

	public void testShowSprintBacklogTreeListInfo() throws Exception {
		List<String> idList = mCreateSprint.getSprintIDList();
		int sprintId = Integer.parseInt(idList.get(0));
		int storyCount = 1;
		int storyEst = 2;
		AddStoryToSprint addStoryToSprint = new AddStoryToSprint(storyCount, storyEst, sprintId, mCreateProject, CreateProductBacklog.TYPE_ESTIMATION);
		addStoryToSprint.exe();

		Thread.sleep(1000);

		int taskCount = 1;
		int taskEst = 2;
		AddTaskToStory addTaskToStory = new AddTaskToStory(taskCount, taskEst, addStoryToSprint, mCreateProject);
		addTaskToStory.exe();

		// ================ set request info ========================
		String projectName = mProject.getName();
		String issueId = String.valueOf(addTaskToStory.getTaskIDList().get(0));
		request.setHeader("Referer", "?PID=" + projectName);
		addRequestParameter("sprintID", idList.get(0));
		addRequestParameter("issueID", issueId);
		String expectedTaskName = addTaskToStory.getTaskList().get(0).getSummary();
		String expectedIssueType = addTaskToStory.getTaskList().get(0).getCategory();
		String expectedLink = "/ezScrum/showIssueInformation.do?issueID=" + issueId;
		List<String> expectedDescription = genArrayList("Create Task #2", "Append to Story #1");
		List<String> expectedHistoryType = genArrayList("", "");

		// ================ set session info ========================
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());

		// ================ 執行 action ==============================
		actionPerform();

		// ================ assert =============================
		verifyNoActionErrors();
		verifyNoActionMessages();
		// assert response text
		String actualResponseText = response.getWriterBuffer().toString();

		JSONObject historyObj = new JSONObject(actualResponseText);

		assertEquals(expectedTaskName, historyObj.getString("Name"));
		assertEquals(expectedIssueType, historyObj.getString("IssueType"));
		assertEquals(expectedLink, historyObj.getString("Link"));
		assertData(expectedHistoryType, expectedDescription, historyObj.getJSONArray("IssueHistories"));
	}

	/**
	 * Story History 的測試 add 1 sprint, 1 story
	 */
	public void testShowStoryHistoryTest1() throws Exception {
		// 加入1個 Sprint
		int sprintId = Integer.valueOf(mCreateSprint.getSprintIDList().get(0));
		// Sprint 加入1個 Story
		AddStoryToSprint addStoryToSprint = new AddStoryToSprint(1, 1, sprintId, mCreateProject, CreateProductBacklog.TYPE_ESTIMATION);
		addStoryToSprint.exe();
		long storyId = addStoryToSprint.getIssueList().get(0).getIssueID();

		// ================ set request info ========================
		String projectName = mProject.getName();
		request.setHeader("Referer", "?PID=" + projectName);
		// 設定 Session 資訊
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());
		request.getSession().setAttribute("Project", mProject);
		// 設定新增 Task 所需的資訊
		String expectedStoryId = String.valueOf(storyId);
		String expectedSprintId = String.valueOf(sprintId);
		List<String> expectedDescription = genArrayList("Create Story #" + storyId, "Append to Sprint #1");
		List<String> expectedHistoryType = genArrayList("", "");

		addRequestParameter("sprintID", expectedSprintId);
		addRequestParameter("issueID", expectedStoryId);

		// ================ 執行 action ======================
		actionPerform();

		// ================ assert ========================
		verifyNoActionErrors();
		verifyNoActionMessages();
		String actualResponseText = response.getWriterBuffer().toString();

		JSONObject historyObj = new JSONObject(actualResponseText);

		assertEquals(addStoryToSprint.getIssueList().get(0).getCategory(), historyObj.getString("IssueType"));
		assertEquals(addStoryToSprint.getIssueList().get(0).getIssueLink(), historyObj.getString("Link"));
		assertEquals(addStoryToSprint.getIssueList().get(0).getSummary(), historyObj.getString("Name"));
		assertData(expectedHistoryType, expectedDescription, historyObj.getJSONArray("IssueHistories"));
	}

	/**
	 * Story History 的測試 add 1 sprint, 1 story and 1 task
	 */
	public void testShowStoryHistoryTest2() throws Exception {
		// 加入1個 Sprint
		int sprintId = Integer.valueOf(mCreateSprint.getSprintIDList().get(0));
		// Sprint 加入1個 Story
		AddStoryToSprint addStoryToSprint = new AddStoryToSprint(1, 1, sprintId, mCreateProject, CreateProductBacklog.TYPE_ESTIMATION);
		addStoryToSprint.exe();
		Thread.sleep(1000);
		long storyId = addStoryToSprint.getIssueList().get(0).getIssueID();

		// Story 加入1個 Task
		AddTaskToStory addTaskToStory = new AddTaskToStory(1, 1, addStoryToSprint, mCreateProject);
		addTaskToStory.exe();

		// ================ set request info ========================
		String projectName = mProject.getName();
		request.setHeader("Referer", "?PID=" + projectName);
		// 設定 Session 資訊
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());
		request.getSession().setAttribute("Project", mProject);
		// 設定新增 Task 所需的資訊
		String expectedStoryId = String.valueOf(storyId);
		String expectedSprintId = String.valueOf(sprintId);
		List<String> expectedDescription = genArrayList("Create Story #1", "Append to Sprint #1", "Add Task #2");
		List<String> expectedHistoryType = genArrayList("", "", "");

		addRequestParameter("sprintID", expectedSprintId);
		addRequestParameter("issueID", expectedStoryId);

		// ================ 執行 action ======================
		actionPerform();

		// ================ assert ========================
		verifyNoActionErrors();
		verifyNoActionMessages();
		String actualResponseText = response.getWriterBuffer().toString();

		JSONObject historyObj = new JSONObject(actualResponseText);

		assertEquals(addStoryToSprint.getIssueList().get(0).getCategory(), historyObj.getString("IssueType"));
		assertEquals(addStoryToSprint.getIssueList().get(0).getIssueLink(), historyObj.getString("Link"));
		assertEquals(addStoryToSprint.getIssueList().get(0).getSummary(), historyObj.getString("Name"));
		assertData(expectedHistoryType, expectedDescription, historyObj.getJSONArray("IssueHistories"));
	}

	/**
	 * Story History 的測試 add 1 sprint, 1 story and 1 task and drop task
	 */
	public void testShowStoryHistoryTest3() throws Exception {
		// 加入1個 Sprint
		int sprintId = Integer.valueOf(mCreateSprint.getSprintIDList().get(0));
		// Sprint 加入1個 Story
		AddStoryToSprint addStoryToSprint = new AddStoryToSprint(1, 1, sprintId, mCreateProject, CreateProductBacklog.TYPE_ESTIMATION);
		addStoryToSprint.exe();
		Thread.sleep(1000);

		long storyId = addStoryToSprint.getIssueList().get(0).getIssueID();
		// Story 加入1個 Task
		AddTaskToStory addTaskToStory = new AddTaskToStory(1, 1, addStoryToSprint, mCreateProject);
		addTaskToStory.exe();
		Thread.sleep(1000);
		long taskId = addTaskToStory.getTaskIDList().get(0);

		// drop Task from story
		DropTask dropTask = new DropTask(mCreateProject, sprintId, storyId, taskId);
		dropTask.exe();

		// ================ set request info ========================
		String projectName = mProject.getName();
		request.setHeader("Referer", "?PID=" + projectName);
		// 設定 Session 資訊
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());
		request.getSession().setAttribute("Project", mProject);
		// 設定新增 Task 所需的資訊
		String expectedStoryId = String.valueOf(storyId);
		String expectedSprintId = String.valueOf(sprintId);
		List<String> expectedDescription = genArrayList("Create Story #1", "Append to Sprint #1", "Add Task #2", "Drop Task #2");
		List<String> expectedHistoryType = genArrayList("", "", "", "");

		addRequestParameter("sprintID", expectedSprintId);
		addRequestParameter("issueID", expectedStoryId);

		// ================ 執行 action ====================
		actionPerform();

		// ================ assert ========================
		verifyNoActionErrors();
		verifyNoActionMessages();
		String actualResponseText = response.getWriterBuffer().toString();

		JSONObject historyObj = new JSONObject(actualResponseText);

		assertEquals(addStoryToSprint.getIssueList().get(0).getCategory(), historyObj.getString("IssueType"));
		assertEquals(addStoryToSprint.getIssueList().get(0).getIssueLink(), historyObj.getString("Link"));
		assertEquals(addStoryToSprint.getIssueList().get(0).getSummary(), historyObj.getString("Name"));
		assertData(expectedHistoryType, expectedDescription, historyObj.getJSONArray("IssueHistories"));
	}

	/**
	 * test add 1 task
	 */
	public void testShowTaskHistoryTest1() throws Exception {
		// 加入1個 Sprint
		long sprintId = Long.valueOf(mCreateSprint.getSprintIDList().get(0));
		// Sprint 加入1個 Story
		AddStoryToSprint addStoryToSprint = new AddStoryToSprint(1, 1, (int) sprintId, mCreateProject, CreateProductBacklog.TYPE_ESTIMATION);
		addStoryToSprint.exe();
		Thread.sleep(1000);

		// Story 加入1個 Task
		AddTaskToStory addTaskToStory = new AddTaskToStory(1, 1, addStoryToSprint, mCreateProject);
		addTaskToStory.exe();
		Thread.sleep(1000);

		// ================ set request info ========================
		String projectName = mProject.getName();
		long taskId = addTaskToStory.getTaskIDList().get(0);

		// 設定 Session 資訊
		request.setHeader("Referer", "?PID=" + projectName);
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());
		request.getSession().setAttribute("Project", mProject);

		// 先 assert story 的 history
		addRequestParameter("sprintID", String.valueOf(sprintId));
		addRequestParameter("issueID", String.valueOf(taskId));

		// 執行 action
		actionPerform();
		verifyNoActionErrors();
		verifyNoActionMessages();

		// get assert data
		String actualResponseText = response.getWriterBuffer().toString();
		JSONObject historyObj = new JSONObject(actualResponseText);
		List<String> expectedDescription = genArrayList("Create Task #2", "Append to Story #1");
		List<String> expectedHistoryType = genArrayList("", "");

		assertData(expectedHistoryType, expectedDescription, historyObj.getJSONArray("IssueHistories"));
	}
	
	/**
	 * edit task info
	 */
	public void testShowTaskHistoryTest2() throws Exception {
		// 加入1個 Sprint
		long sprintId = Long.valueOf(mCreateSprint.getSprintIDList().get(0));
		// Sprint 加入1個 Story
		AddStoryToSprint addStoryToSprint = new AddStoryToSprint(1, 1, (int) sprintId, mCreateProject, CreateProductBacklog.TYPE_ESTIMATION);
		addStoryToSprint.exe();
		Thread.sleep(1000);

		// Story 加入1個 Task
		AddTaskToStory addTaskToStory = new AddTaskToStory(1, 1, addStoryToSprint, mCreateProject);
		addTaskToStory.exe();
		Thread.sleep(1000);

		String projectName = mProject.getName();
		long taskId = addTaskToStory.getTaskIDList().get(0);

		// edit task info
		SprintBacklogLogic SBLogic = new SprintBacklogLogic(mProject, mConfig.getUserSession(), Long.toString(sprintId));
		SBLogic.editTask(taskId, "崩潰啦", "13", "13", "admin", "", "13", "煩死啦", null);
		
		// ================ set request info ========================
		// 設定 Session 資訊
		request.setHeader("Referer", "?PID=" + projectName);
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());
		request.getSession().setAttribute("Project", mProject);

		// 先 assert story 的 history
		addRequestParameter("sprintID", String.valueOf(sprintId));
		addRequestParameter("issueID", String.valueOf(taskId));

		// 執行 action
		actionPerform();
		verifyNoActionErrors();
		verifyNoActionMessages();

		// get assert data
		String actualResponseText = response.getWriterBuffer().toString();
		JSONObject historyObj = new JSONObject(actualResponseText);
		List<String> expectedDescription = genArrayList("Create Task #2",
												        "Append to Story #1",
												        "admin",
												        "Not Check Out => Check Out",
												        "\"TEST_TASK_1\" => \"崩潰啦\"",
												        "1 => 13",
												        "1 => 13",
												        "\"TEST_TASK_NOTES_1\" => \"煩死啦\"",
												        "0 => 13");
		List<String> expectedHistoryType = genArrayList("", "", "Handler", "Status", "Name",
													"Estimate", "Remains", "Note", "Actual Hour");

		assertData(expectedHistoryType, expectedDescription, historyObj.getJSONArray("IssueHistories"));
	}

	/**
	 * change task ststus
	 */
	public void testShowTaskHistoryTest3() throws Exception {
		// 加入1個 Sprint
		long sprintId = Long.valueOf(mCreateSprint.getSprintIDList().get(0));
		// Sprint 加入1個 Story
		AddStoryToSprint addStoryToSprint = new AddStoryToSprint(1, 1, (int) sprintId, mCreateProject, CreateProductBacklog.TYPE_ESTIMATION);
		addStoryToSprint.exe();
		Thread.sleep(1000);

		// Story 加入1個 Task
		AddTaskToStory addTaskToStory = new AddTaskToStory(1, 1, addStoryToSprint, mCreateProject);
		addTaskToStory.exe();
		Thread.sleep(1000);

		String projectName = mProject.getName();
		long taskId = addTaskToStory.getTaskIDList().get(0);

		SprintBacklogHelper SBHelper = new SprintBacklogHelper(mProject, mConfig.getUserSession(), Long.toString(sprintId));
		// task Not Check Out -> Check Out
		IIssue task = SBHelper.getIssue(taskId);
		SBHelper.checkOutTask(taskId, task.getSummary(), task.getAssignto(), task.getPartners(), task.getNotes(), "");
		// task Check Out -> Done
		task = SBHelper.getIssue(taskId);
		SBHelper.doneIssue(taskId, task.getSummary(), task.getNotes(), "", task.getActualHour());
		// task Done -> Check Out
		task = SBHelper.getIssue(taskId);
		SBHelper.reopenIssue(taskId, task.getSummary(), task.getNotes(), "");
		// task Check Out -> Not Check Out
		task = SBHelper.getIssue(taskId);
		SBHelper.resetTask(taskId, task.getSummary(), task.getNotes(), "");
		// ================ set request info ========================
		// 設定 Session 資訊
		request.setHeader("Referer", "?PID=" + projectName);
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());
		request.getSession().setAttribute("Project", mProject);

		// 先 assert story 的 history
		addRequestParameter("sprintID", String.valueOf(sprintId));
		addRequestParameter("issueID", String.valueOf(taskId));

		// 執行 action
		actionPerform();
		verifyNoActionErrors();
		verifyNoActionMessages();

		// get assert data
		String actualResponseText = response.getWriterBuffer().toString();
		JSONObject historyObj = new JSONObject(actualResponseText);
		List<String> expectedDescription = genArrayList("Create Task #2",
												        "Append to Story #1",
												        "Not Check Out => Check Out",
												        "1 => 0",
												        "Not Check Out => Done",
												        "Done => Check Out",
												        "Check Out => Not Check Out");
		List<String> expectedHistoryType = genArrayList("", "", "Status", "Remains", "Status", "Status", "Status");

//		System.out.println("=> " + historyObj.getJSONArray("IssueHistories").toString());
		assertData(expectedHistoryType, expectedDescription, historyObj.getJSONArray("IssueHistories"));
	}

	/**
	 * UnplanedItem History 的測試 1 unplanedItem without editing
	 */
	public void testShowUnplanedItemHistoryTest1() throws Exception {
		// 新增一個 UnplannedItem
		mCreateUnplanned = new CreateUnplannedItem(1, mCreateProject, mCreateSprint);
		mCreateUnplanned.exe();
		String issueId = String.valueOf(mCreateUnplanned.getIdList().get(0));

		// ================== set parameter info ====================
		addRequestParameter("sprintID", mCreateSprint.getSprintIDList().get(0));
		addRequestParameter("issueID", issueId);

		// ================ set session info ========================
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());
		request.getSession().setAttribute("Project", mProject);

		// ================ set session info ========================
		// SessionManager 會對 URL 的參數作分析 ,未帶入此參數無法存入 session
		request.setHeader("Referer", "?PID=" + mProject.getName());

		// 執行 action
		actionPerform();

		// 驗證回傳 path
		verifyForwardPath(null);
		verifyForward(null);
		verifyNoActionErrors();

		List<String> expectedDescription = genArrayList("Create Unplanned #1", "Append to Sprint #1");
		List<String> expectedHistoryType = genArrayList("", "");
		String actualResponseText = response.getWriterBuffer().toString();
		JSONObject historyObj = new JSONObject(actualResponseText);

		assertEquals(mCreateUnplanned.getIssueList().get(0).getCategory(), historyObj.getString("IssueType"));
		assertEquals(mCreateUnplanned.getIssueList().get(0).getIssueLink(), historyObj.getString("Link"));
		assertEquals(mCreateUnplanned.getIssueList().get(0).getSummary(), historyObj.getString("Name"));
		assertData(expectedHistoryType, expectedDescription, historyObj.getJSONArray("IssueHistories"));
	}

	/**
	 * UnplanedItem History 的測試 1 unplanedItem with editing to check out
	 */
	public void testShowUnplanedItemHistoryTest2() throws Exception {
		// ================== init ====================
		CreateAccount createAccount = new CreateAccount(1);
		createAccount.exe();

		Thread.sleep(1000);

		AddUserToRole addUserToRole = new AddUserToRole(mCreateProject, createAccount);
		addUserToRole.exe_ST();

		Thread.sleep(1000);

		// 新增一個 UnplannedItem
		mCreateUnplanned = new CreateUnplannedItem(1, mCreateProject, mCreateSprint);
		mCreateUnplanned.exe();
		EditUnplannedItem EU = new EditUnplannedItem(mCreateUnplanned, mCreateProject, createAccount);
		EU.exe_CO();
		String issueID = String.valueOf(mCreateUnplanned.getIdList().get(0));

		// ================== set parameter info ====================
		addRequestParameter("sprintID", mCreateSprint.getSprintIDList().get(0));
		addRequestParameter("issueID", issueID);

		// ================ set session info ========================
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());
		request.getSession().setAttribute("Project", mProject);

		// ================ set session info ========================
		// SessionManager 會對 URL 的參數作分析 ,未帶入此參數無法存入 session
		request.setHeader("Referer", "?PID=" + mProject.getName());

		// 執行 action
		actionPerform();

		// 驗證回傳 path
		verifyForwardPath(null);
		verifyForward(null);
		verifyNoActionErrors();
		List<String> expectedDescription = genArrayList("Create Unplanned #1", "Append to Sprint #1", "Not Check Out => Check Out", "TEST_ACCOUNT_ID_1",
		        "\"TEST_UNPLANNED_NOTES_1\" => \"i am the update one\"");
		List<String> expectedHistoryType = genArrayList("", "", "Status", "Handler", "Note");
		String actualResponseText = response.getWriterBuffer().toString();
		JSONObject historyObj = new JSONObject(actualResponseText);

		assertEquals(mCreateUnplanned.getIssueList().get(0).getCategory(), historyObj.getString("IssueType"));
		assertEquals(mCreateUnplanned.getIssueList().get(0).getIssueLink(), historyObj.getString("Link"));
		assertEquals(mCreateUnplanned.getIssueList().get(0).getSummary(), historyObj.getString("Name"));
		assertData(expectedHistoryType, expectedDescription, historyObj.getJSONArray("IssueHistories"));
	}

	/**
	 * UnplanedItem History 的測試 1 unplanedItem with editing to done
	 */
	public void testShowUnplanedItemHistoryTest3() throws Exception {
		// ================== init ====================
		CreateAccount createAccount = new CreateAccount(1);
		createAccount.exe();

		Thread.sleep(1000);

		AddUserToRole addUserToRole = new AddUserToRole(mCreateProject, createAccount);
		addUserToRole.exe_ST();

		Thread.sleep(1000);

		mCreateUnplanned = new CreateUnplannedItem(1, mCreateProject, mCreateSprint);
		mCreateUnplanned.exe(); // 新增一個UnplannedItem

		Thread.sleep(1000);

		EditUnplannedItem EU = new EditUnplannedItem(mCreateUnplanned, mCreateProject, createAccount);
		EU.exe_CO();
		Thread.sleep(1000);

		EU = new EditUnplannedItem(mCreateUnplanned, mCreateProject, createAccount);
		EU.exe_DONE();
		String issueId = String.valueOf(mCreateUnplanned.getIdList().get(0));

		// ================== set parameter info ====================
		addRequestParameter("sprintID", mCreateSprint.getSprintIDList().get(0));
		addRequestParameter("issueID", issueId);

		// ================ set session info ========================
		request.getSession().setAttribute("UserSession", mConfig.getUserSession());
		request.getSession().setAttribute("Project", mProject);

		// ================ set session info ========================
		// SessionManager 會對 URL 的參數作分析 ,未帶入此參數無法存入 session
		request.setHeader("Referer", "?PID=" + mProject.getName());

		// 執行 action
		actionPerform();

		// 驗證回傳 path
		verifyForwardPath(null);
		verifyForward(null);
		verifyNoActionErrors();
		List<String> expectedDescription = genArrayList("Create Unplanned #1", "Append to Sprint #1", "Not Check Out => Check Out", "TEST_ACCOUNT_ID_1",
		        "\"TEST_UNPLANNED_NOTES_1\" => \"i am the update one\"", "Check Out => Done");
		List<String> expectedHistoryType = genArrayList("", "", "Status", "Handler", "Note", "Status");
		String actualResponseText = response.getWriterBuffer().toString();
		JSONObject object = new JSONObject(actualResponseText);

		assertEquals(mCreateUnplanned.getIssueList().get(0).getCategory(), object.get("IssueType"));
		assertEquals(mCreateUnplanned.getIssueList().get(0).getIssueLink(), object.get("Link"));
		assertEquals(mCreateUnplanned.getIssueList().get(0).getSummary(), object.get("Name"));
		assertData(expectedHistoryType, expectedDescription, object.getJSONArray("IssueHistories"));
	}

	/**
	 * 傳入若干個字串，組合成arrayList傳出
	 */
	private List<String> genArrayList(String... strs) {
		List<String> arrayList = new ArrayList<String>();
		for (String str : strs) {
			arrayList.add(str);
		}
		return arrayList;
	}

	private void assertData(List<String> expectedHistoryType, List<String> expectedDesc, JSONArray actualData) throws JSONException {
		assertEquals(expectedHistoryType.size(), expectedDesc.size());
		assertEquals(actualData.length(), expectedDesc.size());
		for (int i = 0; i < expectedDesc.size(); i++) {
			JSONObject history = actualData.getJSONObject(i);

			String exceptType = expectedHistoryType.get(i);
			String exceptDesc = expectedDesc.get(i);

			assertEquals(exceptType, history.getString("HistoryType"));
			assertEquals(exceptDesc, history.getString("Description"));
		}
	}
}
