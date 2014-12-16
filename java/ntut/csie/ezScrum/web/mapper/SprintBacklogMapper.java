package ntut.csie.ezScrum.web.mapper;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ntut.csie.ezScrum.dao.HistoryDAO;
import ntut.csie.ezScrum.issue.core.IIssue;
import ntut.csie.ezScrum.issue.core.ITSEnum;
import ntut.csie.ezScrum.issue.internal.Issue;
import ntut.csie.ezScrum.issue.sql.service.core.Configuration;
import ntut.csie.ezScrum.issue.sql.service.internal.MantisService;
import ntut.csie.ezScrum.iteration.core.ISprintPlanDesc;
import ntut.csie.ezScrum.iteration.core.ScrumEnum;
import ntut.csie.ezScrum.pic.core.IUserSession;
import ntut.csie.ezScrum.web.dataObject.HistoryObject;
import ntut.csie.ezScrum.web.databasEnum.IssueTypeEnum;
import ntut.csie.ezScrum.web.logic.SprintPlanLogic;
import ntut.csie.jcis.core.util.DateUtil;
import ntut.csie.jcis.resource.core.IProject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SprintBacklogMapper {
	private static Log log = LogFactory.getLog(SprintBacklogMapper.class);
	private int mSprintPlanId = 0;
	private IProject mProject;
	private ISprintPlanDesc mIterPlanDesc;
	private Date mStartDate;
	private Date mEndDate;
	private IUserSession mUserSession;
	private Configuration mConfig;
	private MantisService mMantisService;
	private double mLimitedPoint = 0;

	private ArrayList<IIssue> mStories = null;
	private List<IIssue> mTasks = null;
	private ArrayList<IIssue> mDropedStories = null;
	private ArrayList<IIssue> mAllIssues = null;

	// 用於紀錄Story與Task之間的Mapping
	LinkedHashMap<Long, IIssue[]> mMapStoryTasks = null;
	LinkedHashMap<Long, IIssue[]> mMapDropedStoryTasks = null;
	// 因使用暫存的方式來加速存取速度,所以當有變動時則需更新
	private boolean mUpdateFlag = true;

	/**
	 * 若沒有指定的話,自動取得目前的 sprint#
	 * 
	 * @param project
	 * @param userSession
	 */
	public SprintBacklogMapper(IProject project, IUserSession userSession) {
		mProject = project;
		mUserSession = userSession;

		SprintPlanLogic sprintPlanLogic = new SprintPlanLogic(project);
		mIterPlanDesc = sprintPlanLogic.loadCurrentPlan();
		if (mIterPlanDesc != null) {
			mSprintPlanId = Integer.parseInt(mIterPlanDesc.getID());
		}

		initSprintInformation();
		initITSInformation();
	}

	/**
	 * 取得指定的 sprint backlog
	 * 
	 * @param project
	 * @param userSession
	 * @param sprintId
	 */
	public SprintBacklogMapper(IProject project, IUserSession userSession, int sprintId) {
		mProject = project;
		mUserSession = userSession;

		SprintPlanMapper mapper = new SprintPlanMapper(project);
		mIterPlanDesc = mapper.getSprintPlan(Integer.toString(sprintId));
		mSprintPlanId = Integer.parseInt(mIterPlanDesc.getID());

		initSprintInformation();
		initITSInformation();
	}

	/**
	 * 初始化 Sprint 的資訊
	 */
	private void initSprintInformation() {
		try {
			if (mIterPlanDesc.getStartDate().equals("")) {
				throw new RuntimeException();
			}
			mStartDate = DateUtil.dayFilter(mIterPlanDesc.getStartDate());
			mEndDate = DateUtil.dayFilter(mIterPlanDesc.getEndDate());

			String aDays = mIterPlanDesc.getAvailableDays();
			// 將判斷 aDay:hours can commit 為 0 時, 計算 sprint 天數 * focus factor 的機制移除
			// 改為只計算 aDay:hours can commit * focus factor
			if (aDays != null && !aDays.equals("")) {
				mLimitedPoint = Integer.parseInt(aDays) * Integer.parseInt(mIterPlanDesc.getFocusFactor()) / 100;
			}
		} catch (NumberFormatException e) {
			log.info("non-exist sprint");
		}
	}

	/**
	 * 初始 mMantisService 的設定
	 */
	private void initITSInformation() {
		mConfig = new Configuration(mUserSession);
		mMantisService = new MantisService(mConfig);
	}

	/**
	 * 測試用
	 */
	public void forceRefresh() {
		getAllIssuesInSprint();
		mUpdateFlag = false;
	}

	/************************************************************
	 * =============== Sprint Backlog的操作 =========
	 *************************************************************/

	public Map<Long, IIssue[]> getTasksMap() {
		refresh();
		return mMapStoryTasks;
	}

	/**
	 * 如果沒有指定時間的話，預設就回傳目前最新的Task表給他
	 * 
	 * @return
	 */
	public List<IIssue> getTasks() {
		refresh();
		return mTasks;
	}

	public Map<Long, IIssue[]> getDropedTaskMap() {
		if (mMapDropedStoryTasks == null) {
			getDropedStory();
		}
		return mMapDropedStoryTasks;
	}

	public List<IIssue> getIssues(String category) {
		refresh();

		List<IIssue> stories = new ArrayList<IIssue>();
		if (category.equals(ScrumEnum.STORY_ISSUE_TYPE)) {
			stories.addAll(mStories);
		} else if (category.equals(ScrumEnum.TASK_ISSUE_TYPE)) {
			stories.addAll(mTasks);
		} else {
			stories.addAll(mAllIssues);
		}

		return stories;
	}

	/**
	 * 取得這個Sprint內stories
	 * 
	 * @param sprintId
	 * @return
	 */
	public IIssue[] getStoryInSprint(long sprintId) {
		mMantisService.openConnect();
		IIssue[] stories = mMantisService.getIssues(mProject.getName(), ScrumEnum.STORY_ISSUE_TYPE, null, Long.toString(sprintId), null);
		mMantisService.closeConnect();
		return stories;
	}

	/**
	 * 取得 story 內的 tasks 與 sprint 無關
	 * 
	 * @param storyId
	 */
	public IIssue[] getTaskInStory(long storyId) {
		IIssue story = getIssue(storyId);
		
		if (story == null) {
			return null;
		}
		
		List<Long> taskIDs = story.getChildrenId();
		List<IIssue> tasks = new ArrayList<IIssue>();
		
		for (long taskID : taskIDs) {
			IIssue task = getIssue(taskID);
			if (task != null) {
				tasks.add(task);
			}
		}

		return tasks.toArray(new IIssue[tasks.size()]);
	}

	/**
	 * 取得在這個 Sprint 中曾經被 Drop 掉的 Story
	 */
	public IIssue[] getDropedStory() {
		if (mDropedStories == null) {
			mDropedStories = new ArrayList<IIssue>();
		}
		else {
			return mDropedStories.toArray(new IIssue[mDropedStories.size()]);
		}

		String iter = Integer.toString(mSprintPlanId);
		Date startDate = getSprintStartDate();
		Date endDate = getSprintEndDate();

		mMantisService.openConnect();

		// 找出這個 Sprint 期間，所有可能出現的 issue，下面再進行過濾
		IIssue[] tmpIIssues = mMantisService.getIssues(mProject.getName(), ScrumEnum.STORY_ISSUE_TYPE, null, "*", startDate, endDate);

		// 確認這些這期間被 Drop 掉的 Story 是否曾經有在此 Sprint 過
		if (tmpIIssues != null) {
			for (IIssue issue : tmpIIssues) {
				Map<Date, String> map = issue.getTagValueList("Iteration");

				if (!issue.getSprintID().equals(iter)) {
					mDropedStories.add(issue);
				}
			}
		}

		// 取得這些被 Dropped Story 的 Task
		mMapDropedStoryTasks = new LinkedHashMap<Long, IIssue[]>();
		ArrayList<IIssue> tmpList = new ArrayList<IIssue>();
		for (IIssue issue : mDropedStories) {
			tmpList.clear();

			List<Long> childList = issue.getChildrenId();

			for (Long id : childList) {
				IIssue tmp = mMantisService.getIssue(id);
				if (tmp != null) tmpList.add(tmp);
			}
			mMapDropedStoryTasks.put(issue.getIssueID(), tmpList.toArray(new IIssue[tmpList.size()]));
		}

		mMantisService.closeConnect();
		return mDropedStories.toArray(new IIssue[mDropedStories.size()]);
	}

	public IIssue getIssue(long issueId) {
		mMantisService.openConnect();
		IIssue issue = mMantisService.getIssue(issueId);
		mMantisService.closeConnect();
		return issue;
	}

	public void modifyTaskInformation(long taskId, String name, String handler, Date modifyDate) {
		IIssue task = this.getIssue(taskId);

		// taskID 為不存在的 issue 時，會有 null 的危險
		if (task != null) {
			if ((handler != null) && (handler.length() > 0) && (!task.getAssignto().equals(handler))) {
				MantisService service = new MantisService(mConfig);
				service.openConnect();
				service.updateHandler(task, handler, modifyDate);
				service.closeConnect();
			}
			if (!task.getSummary().equals(name) && name != null) {
				mMantisService.openConnect();
				mMantisService.updateName(task, name, modifyDate);
				mMantisService.closeConnect();
			}
		}
	}

	public void updateTagValue(IIssue issue) {
		mMantisService.openConnect();
		mMantisService.updateBugNote(issue);
		// 因使用暫存的方式來加速存取速度,所以當有變動時則需更新
		mUpdateFlag = true;
		mMantisService.closeConnect();
	}

	public long addTask(String name, String description, long storyId, Date date) {
		mMantisService.openConnect();
		IIssue task = new Issue();

		task.setProjectID(mProject.getName());
		task.setSummary(name);
		task.setDescription(description);
		task.setCategory(ScrumEnum.TASK_ISSUE_TYPE);

		task = this.getIssue(mMantisService.newIssue(task));

		// 新增關係
		mMantisService.openConnect(); // connection be closed by SprintBacklogMapper.getIssue
		mMantisService.addRelationship(storyId, task.getIssueID(), ITSEnum.PARENT_RELATIONSHIP, date);

		// 因使用暫存的方式來加速存取速度,所以當有變動時則需更新
		mUpdateFlag = true;
		mMantisService.closeConnect();
		return task.getIssueID();
	}

	public void addExistedTask(long[] taskIds, long storyId, Date date) {
		mMantisService.openConnect();
		
		// 新增關係
		for (long taskId : taskIds) {
			mMantisService.addRelationship(storyId, taskId, ITSEnum.PARENT_RELATIONSHIP, date);
		}

		// 因使用暫存的方式來加速存取速度,所以當有變動時則需更新
		mUpdateFlag = true;
		mMantisService.closeConnect();
	}

	public void deleteExistedTask(long[] taskIds) {
		mMantisService.openConnect();
		
		for (long taskId : taskIds) {
			mMantisService.deleteTask(taskId);
		}
		mUpdateFlag = true;
		mMantisService.closeConnect();
	}

	public void removeTask(long taskId, long parentId) {
		mMantisService.openConnect();
		mMantisService.removeRelationship(parentId, taskId, ITSEnum.PARENT_RELATIONSHIP);
		// 因使用暫存的方式來加速存取速度,所以當有變動時則需更新
		mUpdateFlag = true;
		mMantisService.closeConnect();
	}

	/************************************************************
	 * ===================== 取得Iteration的描述 ====================
	 *************************************************************/

	public int getSprintPlanId() {
		return mSprintPlanId;
	}

	public IProject getProject() {
		return mProject;
	}

	public Date getSprintStartDate() {
		return mStartDate;
	}

	public Date getSprintEndDate() {
		return mEndDate;
	}

	public double getLimitedPoint() {
		return mLimitedPoint;
	}

	public String getSprintGoal() {
		return mIterPlanDesc.getGoal();
	}

	/************************************************************
	 * ================== TaskBoard 中有關於 task 操作 ================
	 *************************************************************/

	public void doneIssue(long id, String bugNote, String changeDate) {
		Date closeDate = null;
		
		if (changeDate != null && !changeDate.equals("")) {
			closeDate = DateUtil.dayFillter(changeDate, DateUtil._16DIGIT_DATE_TIME);
		}
		
		mMantisService.openConnect();
		mMantisService.changeStatusToClosed(id, ITSEnum.FIXED_RESOLUTION, bugNote, closeDate);
		mMantisService.closeConnect();
	}

	public void reopenIssue(long id, String name, String bugNote, String changeDate) {
		Date reopenDate = new Date();
		
		if (changeDate != null && !changeDate.equals("")) {
			reopenDate = DateUtil.dayFillter(changeDate, DateUtil._16DIGIT_DATE_TIME);
		}

		IIssue issue = getIssue(id);
		mMantisService.openConnect();
		
		if (issue.getCategory().equals(ScrumEnum.STORY_ISSUE_TYPE)) {
			mMantisService.resetStatusToNew(id, name, bugNote, reopenDate);
		} else if (issue.getCategory().equals(ScrumEnum.TASK_ISSUE_TYPE))  {
			mMantisService.reopenStatusToAssigned(id, name, bugNote, reopenDate);
		}
		
		mMantisService.closeConnect();
	}

	public void resetTask(long id, String name, String bugNote, String changeDate) {
		Date reopenDate = new Date();
		
		if (changeDate != null && !changeDate.equals("")) {
			reopenDate = DateUtil.dayFillter(changeDate, DateUtil._16DIGIT_DATE_TIME);
		}

		mMantisService.openConnect();
		mMantisService.resetStatusToNew(id, name, bugNote, reopenDate);
		mMantisService.closeConnect();
	}

	public void checkOutTask(long id, String bugNote) {
		if (bugNote != null && !bugNote.equals("")) {
			mMantisService.openConnect();
			mMantisService.insertBugNote(id, bugNote);
			mMantisService.closeConnect();
		}
	}

	public void deleteTask(long taskId, long parentId) {
		mMantisService.openConnect();
		mMantisService.deleteRelationship(parentId, taskId);
		mMantisService.deleteTask(taskId);
		mUpdateFlag = true;
		mMantisService.closeConnect();
	}

	/************************************************************
	 * private methods
	 *************************************************************/

	/**
	 * Refresh 動作
	 */
	private void refresh() {
		if (mStories == null || mTasks == null || mAllIssues == null || 
			mMapStoryTasks == null || mUpdateFlag) {
			getAllIssuesInSprint();
			mUpdateFlag = false;
		}
	}

	/**
	 * 取得目前所有在此 Sprint 的 Story 與 Task
	 * @return
	 */
	private IIssue[] getAllIssuesInSprint() {
		if (mStories == null || mTasks == null || mAllIssues == null ||
				mMapStoryTasks == null) {
			mStories = new ArrayList<IIssue>();
			mTasks = new ArrayList<IIssue>();
			mAllIssues = new ArrayList<IIssue>();
			mMapStoryTasks = new LinkedHashMap<Long, IIssue[]>();
		} else {
			mStories.clear();
			mTasks.clear();
			mAllIssues.clear();
			mMapStoryTasks.clear();
		}

		IIssue[] issues = getStoryInSprint(mSprintPlanId);

		for (IIssue issue : issues) {
			mStories.add(issue);
			IIssue[] taskList = getTaskInStory(issue.getIssueID());
			if (taskList.length != 0) {
				for (IIssue task : taskList) {
					mTasks.add(task);
				}
				mMapStoryTasks.put(issue.getIssueID(), taskList);
			}
		}
		mUpdateFlag = false;

		mAllIssues.addAll(mStories);
		mAllIssues.addAll(mTasks);
		return mAllIssues.toArray(new IIssue[mAllIssues.size()]);
	}
}
