package ntut.csie.ezScrum.web.action.backlog;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ntut.csie.ezScrum.pic.core.IUserSession;
import ntut.csie.ezScrum.web.action.PermissionAction;
import ntut.csie.ezScrum.web.helper.SprintBacklogHelper;
import ntut.csie.ezScrum.web.support.SessionManager;
import ntut.csie.jcis.resource.core.IProject;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

public class AjaxRemoveSprintTaskAction extends PermissionAction {

	@Override
	public boolean isValidAction() {
		return super.getScrumRole().getAccessSprintBacklog();
	}

	@Override
	public boolean isXML() {
		// XML
		return true;
	}

	@Override
	public StringBuilder getResponse(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) {
		
		//  get session info
		IProject project = (IProject) SessionManager.getProject(request);
		IUserSession session = (IUserSession) request.getSession().getAttribute("UserSession");
		
		//  get parameter info
		String sprintId = request.getParameter("sprintID");
		long taskId = Long.parseLong(request.getParameter("issueID"));
		long parentId = Long.parseLong(request.getParameter("parentID"));

		//  remove the task and clear its info
		SprintBacklogHelper sprintBacklogHelper = new SprintBacklogHelper(project, session, sprintId);
		sprintBacklogHelper.dropTask(taskId);
		
		StringBuilder result = new StringBuilder();
		result.append("<DropTask><Result>true</Result><Task><Id>")
				.append(taskId)
				.append("</Id></Task></DropTask>");
		
		return result;
	}
}
