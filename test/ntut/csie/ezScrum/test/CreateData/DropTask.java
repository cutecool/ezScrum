package ntut.csie.ezScrum.test.CreateData;

import ntut.csie.ezScrum.issue.sql.service.core.Configuration;
import ntut.csie.ezScrum.pic.core.IUserSession;
import ntut.csie.ezScrum.web.mapper.SprintBacklogMapper;
import ntut.csie.jcis.resource.core.IProject;

public class DropTask {
	private Configuration mConfig = new Configuration();
	private CreateProject mCP;
	private long mSprintId = 0;
	private long mTaskId = 0;
	
	public DropTask(CreateProject createProject, long sprintId, long parentId, long taskId) {
		mCP = createProject;
		mSprintId = sprintId;
		mTaskId = taskId;
	}

	public void exe() {
		IProject project = mCP.getProjectList().get(0);
		IUserSession userSession = mConfig.getUserSession();
		SprintBacklogMapper sprintBacklogMapper = new SprintBacklogMapper(project, userSession, mSprintId);
		sprintBacklogMapper.dropTask(mTaskId);
	}
}
