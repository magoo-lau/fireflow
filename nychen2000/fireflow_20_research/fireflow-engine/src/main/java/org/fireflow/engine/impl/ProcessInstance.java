/**
 * Copyright 2007-2008 非也
 * All rights reserved. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation。
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses. *
 */
package org.fireflow.engine.impl;

// Generated Feb 23, 2008 12:04:21 AM by Hibernate Tools 3.2.0.b9
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fireflow.engine.EngineException;
import org.fireflow.engine.IProcessInstance;
import org.fireflow.engine.IRuntimeContextAware;
import org.fireflow.engine.ITaskInstance;
import org.fireflow.engine.IWorkflowSession;
import org.fireflow.engine.IWorkflowSessionAware;
import org.fireflow.engine.RuntimeContext;
import org.fireflow.engine.definition.WorkflowDefinition;
import org.fireflow.engine.event.IProcessInstanceEventListener;
import org.fireflow.engine.event.ProcessInstanceEvent;
import org.fireflow.engine.persistence.IPersistenceService;
import org.fireflow.kernel.IJoinPoint;
import org.fireflow.kernel.INetInstance;
import org.fireflow.kernel.ISynchronizerInstance;
import org.fireflow.kernel.IToken;
import org.fireflow.kernel.KernelException;
import org.fireflow.kernel.impl.JoinPoint;
import org.fireflow.model.EventListener;
import org.fireflow.model.WorkflowProcess;

/**
 * ProcessInstance generated by hbm2java
 */
@SuppressWarnings("serial")
public class ProcessInstance implements IProcessInstance, IRuntimeContextAware, IWorkflowSessionAware, java.io.Serializable {

    private String id = null;
    private String processId = null;
    private Integer version = null;
    private String name = null;
    private String displayName = null;
    private Integer state = null;
    private Boolean suspended = null;
    private String creatorId = null;
    private Date createdTime = null;
    private Date startedTime = null;
    private Date endTime = null;
    private Date expiredTime = null;
    private String parentProcessInstanceId = null;
    private String parentTaskInstanceId = null;

    //null表示尚未初始化
    private Map<String, Object> processInstanceVariables = null;//new HashMap<String, Object>();
    
    
    protected transient RuntimeContext rtCtx = null;
    protected transient IWorkflowSession workflowSession = null;

    public void setRuntimeContext(RuntimeContext ctx) {
        this.rtCtx = ctx;
    }

    public RuntimeContext getRuntimeContext() {
        return this.rtCtx;
    }

    public ProcessInstance() {
        this.state = IProcessInstance.INITIALIZED;
        this.suspended = false;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProcessId() {
        return this.processId;
    }

    public void setProcessId(String processID) {
        this.processId = processID;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String label) {
        this.displayName = label;
    }

    public Integer getState() {
        return this.state;
    }

    public void setState(Integer state) {
        this.state = state;
    }

    public String getParentProcessInstanceId() {
        return parentProcessInstanceId;
    }

    public void setParentProcessInstanceId(String parentProcessInstanceId) {
        this.parentProcessInstanceId = parentProcessInstanceId;
    }

    /**
     * 生成joinPoint 
     * @param synchInst
     * @param token
     * @return
     * @throws EngineException
     */
    public IJoinPoint createJoinPoint(ISynchronizerInstance synchInst, IToken token) throws EngineException {

        int enterTransInstanceCount = synchInst.getEnteringTransitionInstances().size();
        if (enterTransInstanceCount == 0) {//检查流程定义是否合法，同步器节点必须有输入边

            throw new EngineException(this.getId(), this.getWorkflowProcess(),
                    synchInst.getSynchronizer().getId(), "The process definition [" + this.getName() + "] is invalid，the synchronizer[" + synchInst.getSynchronizer() + "] has no entering transition");
        }
        IPersistenceService persistenceService = rtCtx.getPersistenceService();
        //保存到数据库
        persistenceService.saveOrUpdateToken(token);

        IJoinPoint resultJoinPoint = null;
        resultJoinPoint = new JoinPoint();
        resultJoinPoint.setProcessInstance(this);
        resultJoinPoint.setSynchronizerId(synchInst.getSynchronizer().getId());
        if (enterTransInstanceCount == 1) {
            // 生成一个不存储到数据库中的JoinPoint
            resultJoinPoint.addValue(token.getValue());

            if (token.isAlive()) {
                resultJoinPoint.setAlive(true);
                resultJoinPoint.setFromActivityId(token.getFromActivityId());
            }
            resultJoinPoint.setStepNumber(token.getStepNumber() + 1);

            return resultJoinPoint;
        } else {

            int stepNumber = 0;

            List<IToken> tokensList_0 = persistenceService.findTokensForProcessInstance(this.getId(), synchInst.getSynchronizer().getId());
            Map<String,IToken> tokensMap = new HashMap<String,IToken>();
            for (int i = 0; i < tokensList_0.size(); i++) {
                IToken tmpToken =   tokensList_0.get(i);
                String tmpFromActivityId = tmpToken.getFromActivityId();
                if (!tokensMap.containsKey(tmpFromActivityId)) {
                    tokensMap.put(tmpFromActivityId, tmpToken);
                } else {
                	//TODO  ====下面的代码有意义吗？===start===wmj2003
                    IToken tmpToken2 = tokensMap.get(tmpFromActivityId);
                    if (tmpToken2.getStepNumber() > tmpToken.getStepNumber()) {
                        tokensMap.put(tmpFromActivityId, tmpToken2);
                    }
                   //TODO  ====下面的代码有意义吗？===end===wmj2003
                }
            }

            List<IToken> tokensList = new ArrayList<IToken>(tokensMap.values());

            for (int i = 0; i < tokensList.size(); i++) {
                IToken _token = tokensList.get(i);
                resultJoinPoint.addValue(_token.getValue());
                if (_token.isAlive()) {//如果token的状态是alive
                    resultJoinPoint.setAlive(true);
                    String oldFromActivityId = resultJoinPoint.getFromActivityId();
                    if (oldFromActivityId == null || oldFromActivityId.trim().equals("")) {
                        resultJoinPoint.setFromActivityId(_token.getFromActivityId());
                    } else {
                        resultJoinPoint.setFromActivityId(oldFromActivityId + IToken.FROM_ACTIVITY_ID_SEPARATOR + _token.getFromActivityId());
                    }

                }
                if (token.getStepNumber() > stepNumber) {
                    stepNumber = token.getStepNumber();
                }
            }

            resultJoinPoint.setStepNumber(stepNumber + 1);

            return resultJoinPoint;
        }
   
    }

    /* (non-Javadoc)
     * @see org.fireflow.engine.IProcessInstance#run()
     */
    public void run() throws EngineException, KernelException {
        if (this.getState().intValue() != IProcessInstance.INITIALIZED) {
            throw new EngineException(this.getId(),
                    this.getWorkflowProcess(),
                    this.getProcessId(), "The state of the process instance is " + this.getState() + ",can not run it ");
        }

        INetInstance netInstance = rtCtx.getKernelManager().getNetInstance(this.getProcessId(), this.getVersion());
        if (netInstance == null) {
            throw new EngineException(this.getId(),
                    this.getWorkflowProcess(),
                    this.getProcessId(), "The net instance for the  workflow process [Id=" + this.getProcessId() + "] is Not found");
        }
        //触发事件
        ProcessInstanceEvent event = new ProcessInstanceEvent();
        event.setEventType(ProcessInstanceEvent.BEFORE_PROCESS_INSTANCE_RUN);
        event.setSource(this);
        this.fireProcessInstanceEvent(event);

        this.setState(IProcessInstance.RUNNING);
        this.setStartedTime(rtCtx.getCalendarService().getSysDate());
        rtCtx.getPersistenceService().saveOrUpdateProcessInstance(this);
        netInstance.run(this);//运行工作流网实例,从startnode开始
    }

    /* (non-Javadoc)
     * @see org.fireflow.engine.IProcessInstance#getProcessInstanceVariables()
     */
    public Map<String ,Object> getProcessInstanceVariables() {
		IPersistenceService persistenceService = this.rtCtx.getPersistenceService();
    	if (processInstanceVariables==null){
    		//通过数据库查询进行初始化
    		List<ProcessInstanceVar> allVars = persistenceService.findProcessInstanceVariable(this.getId());
    		processInstanceVariables = new HashMap<String ,Object>();
    		if (allVars!=null && allVars.size()!=0){
    			for (ProcessInstanceVar theVar :allVars){
    				processInstanceVariables.put(theVar.getVarPrimaryKey().getName(), theVar.getValue());
    			}
    		}
    	}    	    	
        return processInstanceVariables;
    }

    public void setProcessInstanceVariables(Map<String ,Object> vars) {
        processInstanceVariables = vars;
//        processInstanceVariables.putAll(vars);
    }

    /* (non-Javadoc)
     * @see org.fireflow.engine.IProcessInstance#getProcessInstanceVariable(java.lang.String)
     */
    public Object getProcessInstanceVariable(String name) {
		IPersistenceService persistenceService = this.rtCtx.getPersistenceService();
    	if (processInstanceVariables==null){
    		//通过数据库查询进行初始化
    		List<ProcessInstanceVar> allVars = persistenceService.findProcessInstanceVariable(this.getId());
    		processInstanceVariables = new HashMap<String ,Object>();
    		if (allVars!=null && allVars.size()!=0){
    			for (ProcessInstanceVar theVar :allVars){
    				processInstanceVariables.put(theVar.getVarPrimaryKey().getName(), theVar.getValue());
    			}
    		}
    	}    	
        return processInstanceVariables.get(name);
    }

    /* (non-Javadoc)
     * @see org.fireflow.engine.IProcessInstance#setProcessInstanceVariable(java.lang.String, java.lang.Object)
     */
    public void setProcessInstanceVariable(String name, Object value) {
		IPersistenceService persistenceService = this.rtCtx.getPersistenceService();
    	if (processInstanceVariables==null){
    		//通过数据库查询进行初始化
    		List<ProcessInstanceVar> allVars = persistenceService.findProcessInstanceVariable(this.getId());
    		processInstanceVariables = new HashMap<String ,Object>();
    		if (allVars!=null && allVars.size()!=0){
    			for (ProcessInstanceVar theVar :allVars){
    				processInstanceVariables.put(theVar.getVarPrimaryKey().getName(), theVar.getValue());
    			}
    		}
    	}
    	ProcessInstanceVar procInstVar = new ProcessInstanceVar();
    	ProcessInstanceVarPk pk = new ProcessInstanceVarPk();
    	pk.setProcessInstanceId(this.getId());
    	pk.setName(name);
    	procInstVar.setVarPrimaryKey(pk);
    	procInstVar.setValue(value);
    	procInstVar.setValueType(value.getClass().getName());
    	
    	if (processInstanceVariables.containsKey(name)){
    		persistenceService.updateProcessInstanceVariable(procInstVar);
    	}else{
    		persistenceService.saveProcessInstanceVariable(procInstVar);
    	}
        processInstanceVariables.put(name, value);
    }

    /* (non-Javadoc)
     * @see org.fireflow.engine.IProcessInstance#getWorkflowProcess()
     */
    public WorkflowProcess getWorkflowProcess() throws EngineException {
        WorkflowDefinition workflowDef = rtCtx.getDefinitionService().getWorkflowDefinitionByProcessIdAndVersionNumber(this.getProcessId(), this.getVersion());
        WorkflowProcess workflowProcess = null;

        workflowProcess = workflowDef.getWorkflowProcess();

        return workflowProcess;
    }

    public String getParentTaskInstanceId() {
        return parentTaskInstanceId;
    }

    public void setParentTaskInstanceId(String taskInstId) {
        parentTaskInstanceId = taskInstId;
    }

    public Date getCreatedTime() {
        return this.createdTime;
    }

    public Date getStartedTime() {
        return this.startedTime;
    }

    public Date getEndTime() {
        return this.endTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public void setStartedTime(Date startedTime) {
        this.startedTime = startedTime;
    }

    /**
     * 正常结束工作流
     * 1、首先检查有无活动的token,如果有则直接返回，如果没有则结束当前流程
     * 2、执行结束流程的操作，将state的值设置为结束状态
     * 3、然后检查parentTaskInstanceId是否为null，如果不为null则，调用父taskinstance的complete操作。
     */
    public void complete() throws EngineException, KernelException {
        List<IToken> tokens = rtCtx.getPersistenceService().findTokensForProcessInstance(this.getId(), null);
        boolean canBeCompleted = true;
        for (int i = 0; tokens != null && i < tokens.size(); i++) {
            IToken token = tokens.get(i);
            if (token.isAlive()) {
                canBeCompleted = false;
                break;
            }
        }
        if (!canBeCompleted) {
            return;
        }

        this.setState(IProcessInstance.COMPLETED);
        //记录结束时间
        this.setEndTime(rtCtx.getCalendarService().getSysDate());
        rtCtx.getPersistenceService().saveOrUpdateProcessInstance(this);
        
        //删除所有的token
        for (int i = 0; tokens != null && i < tokens.size(); i++) {
            IToken token = tokens.get(i);
            rtCtx.getPersistenceService().deleteToken(token);
        }

        //触发事件
        ProcessInstanceEvent event = new ProcessInstanceEvent();
        event.setEventType(ProcessInstanceEvent.AFTER_PROCESS_INSTANCE_COMPLETE);
        event.setSource(this);
        this.fireProcessInstanceEvent(event);
        if (this.getParentTaskInstanceId() != null && !this.getParentTaskInstanceId().trim().equals("")) {
            ITaskInstance taskInstance = rtCtx.getPersistenceService().findAliveTaskInstanceById(this.getParentTaskInstanceId());
            ((IRuntimeContextAware) taskInstance).setRuntimeContext(rtCtx);
            ((IWorkflowSessionAware) taskInstance).setCurrentWorkflowSession(workflowSession);
            ((TaskInstance) taskInstance).complete(null);
        }
    }

    /* (non-Javadoc)
     * @see org.fireflow.engine.IProcessInstance#abort()
     */
    public void abort() throws EngineException {
        if (this.state.intValue() == IProcessInstance.COMPLETED || this.state.intValue() == IProcessInstance.CANCELED) {
            throw new EngineException(this, this.getWorkflowProcess(), "The process instance can not be aborted,the state of this process instance is " + this.getState());
        }
        IPersistenceService persistenceService = rtCtx.getPersistenceService();
        persistenceService.abortProcessInstance(this);
    }


    /**
     * 触发process instance相关的事件
     * @param e
     * @throws org.fireflow.engine.EngineException
     */
    protected void fireProcessInstanceEvent(ProcessInstanceEvent e) throws EngineException {
        WorkflowProcess workflowProcess = this.getWorkflowProcess();
        if (workflowProcess == null) {
            return;
        }

        List<EventListener> listeners = workflowProcess.getEventListeners();
        for (int i = 0; i < listeners.size(); i++) {
            EventListener listener = listeners.get(i);
            Object obj = rtCtx.getBeanByName(listener.getClassName());
            if (obj != null) {
                ((IProcessInstanceEventListener) obj).onProcessInstanceEventFired(e);
            }
        }
    }

    public Date getExpiredTime() {
        return this.expiredTime;
    }

    public void setExpiredTime(Date arg) {
        this.expiredTime = arg;

    }

    public IWorkflowSession getCurrentWorkflowSession() {
        return this.workflowSession;
    }

    public void setCurrentWorkflowSession(IWorkflowSession session) {
        this.workflowSession = session;
    }

    public String getCreatorId() {
        return this.creatorId;
    }

    public void setCreatorId(String s) {
        this.creatorId = s;
    }

    public Boolean isSuspended() {
        return suspended;
    }
    
    public Boolean getSuspended(){
    	return suspended;
    }

    public void setSuspended(Boolean isSuspended) {
        this.suspended = isSuspended;
    }

    /* (non-Javadoc)
     * @see org.fireflow.engine.IProcessInstance#suspend()
     */
    public void suspend() throws EngineException {
        if (this.state == IProcessInstance.COMPLETED || this.state == IProcessInstance.CANCELED) {
            throw new EngineException(this, this.getWorkflowProcess(), "The process instance can not be suspended,the state of this process instance is " + this.state);
        }
        if (this.isSuspended()) {
            return;
        }
        IPersistenceService persistenceService = this.rtCtx.getPersistenceService();
        persistenceService.suspendProcessInstance(this);
    }

    /* (non-Javadoc)
     * @see org.fireflow.engine.IProcessInstance#restore()
     */
    public void restore() throws EngineException {
        if (this.state == IProcessInstance.COMPLETED || this.state == IProcessInstance.CANCELED) {
            throw new EngineException(this, this.getWorkflowProcess(), "The process instance can not be restored,the state of this process instance is " + this.state);
        }
        if (!this.isSuspended()) {
            return;
        }

        IPersistenceService persistenceService = this.rtCtx.getPersistenceService();
        persistenceService.restoreProcessInstance(this);

    }
}
