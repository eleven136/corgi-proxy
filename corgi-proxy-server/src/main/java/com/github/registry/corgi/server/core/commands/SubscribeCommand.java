/*
 * Copyright 2019-2119 gao_xianglong@sina.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.registry.corgi.server.core.commands;

import com.github.registry.corgi.server.Constants;
import com.github.registry.corgi.server.core.ZookeeperConnectionHandler;
import com.github.registry.corgi.server.exceptions.CommandException;
import com.github.registry.corgi.utils.CorgiProtocol;
import com.github.registry.corgi.utils.TransferBo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 订阅命令处理类，用户Consumer订阅寻址使用
 *
 * @author gao_xianglong@sina.com
 * @version 0.1-SNAPSHOT
 * @date created in 2019-06-19 17:32
 */
public class SubscribeCommand extends CorgiCommandTemplate {
    private Map<String, LinkedBlockingQueue<String>> queues;
    private Logger log = LoggerFactory.getLogger("");

    protected SubscribeCommand(CorgiProtocol protocol, ZookeeperConnectionHandler connectionHandler) {
        super(protocol, connectionHandler);
    }

    protected SubscribeCommand(CorgiProtocol protocol, ZookeeperConnectionHandler connectionHandler,
                               Map<String, LinkedBlockingQueue<String>> queues) {
        this(protocol, connectionHandler);
        this.queues = queues;

    }

    @Override
    public TransferBo.Content run(TransferBo transferBo)
            throws CommandException {
        final int index = 1;
        ZookeeperConnectionHandler connectionHandler = super.getConnectionHandler();
        TransferBo.Content content = new TransferBo.Content();
        final String PATH = transferBo.getPersistentNode();
        LinkedBlockingQueue<String> queue = queues.get(PATH);
        try {
            if (null == queue) {
                queue = new LinkedBlockingQueue(Constants.CAPACITY);
                queues.put(PATH, queue);
//                connectionHandler.watch(PATH, new CorgiCallBack() {
//                    @Override
//                    public void execute(String path) {
//                        try {
//                            queue.put(path);
//                        } catch (InterruptedException e) {
//                            log.error("Queue write failure!!!", e);
//                        }
//                    }
//                });
                LinkedBlockingQueue<String> finalQueue = queue;
                //watch,检测到事件流后触发后回调
                connectionHandler.watch(PATH, msg -> {
                    try {
                        finalQueue.put(msg);
                    } catch (InterruptedException e) {
                        log.error("Queue write failure!!!", e);
                    }
                });
                List<String> result = null;
                do {
                    result = connectionHandler.getChildrensSnapshot(PATH); //如果是第一次订阅，则返回本地快照的全量数据
                } while (result.isEmpty());
                content.setPlusNodes(result.toArray(new String[result.size()]));
                for (int i = 0; i < result.size(); i++) {
                    queue.poll();//消费去重,避免全量拉取数据后，再重复消费队列中的数据
                }
            } else {
                //如果开启了批量拉取，超过超时时间则返回，不会一直阻塞
                if (transferBo.isBatch()) {
                    final int pullSize = transferBo.getPullSize();
                    final int timeOut = transferBo.getPullTimeOut() / pullSize;//假设每次拉取10条，总超时时间为10s，那么单条消息的超时时间为1s
                    List<String> plusNodesList = null;
                    List<String> reducesNodesList = null;
                    for (int i = 0; i < pullSize; i++) {
                        final String temp = queue.poll(timeOut, TimeUnit.MILLISECONDS);
                        if (StringUtils.isEmpty(temp)) {
                            continue;
                        }
                        if (temp.startsWith(Constants.PLUS_EVENT)) {
                            if (null == plusNodesList) {
                                plusNodesList = new Vector<>(Constants.INITIAL_CAPACITY);
                            }
                            plusNodesList.add(temp.substring(index));
                        } else if (temp.startsWith(Constants.REDUCES_EVENT)) {
                            if (null == reducesNodesList) {
                                reducesNodesList = new Vector<>(Constants.INITIAL_CAPACITY);
                            }
                            reducesNodesList.add(temp.substring(index));
                        }
                    }
                    addNodes(content, null != plusNodesList ? plusNodesList.toArray(new String[plusNodesList.size()]) : null,
                            null != reducesNodesList ? reducesNodesList.toArray(new String[reducesNodesList.size()]) : null);
                } else {
                    final String temp = queue.take();//阻塞等待,直至有具体的事件发生
                    final String[] nodes = new String[]{temp.substring(index)};
                    if (temp.startsWith(Constants.PLUS_EVENT)) {
                        addNodes(content, nodes, null);
                    } else if (temp.startsWith(Constants.REDUCES_EVENT)) {
                        addNodes(content, null, nodes);
                    }
                }
            }
        } catch (Throwable e) {
            throw new CommandException("Subscribe Command execution failed!!!", e);
        }
        return content;
    }

    /**
     * 添加上/下线事件流
     *
     * @param content
     * @param plusNodes
     * @param reducesNodes
     */
    private void addNodes(TransferBo.Content content, String[] plusNodes, String[] reducesNodes) {
        content.setPlusNodes(plusNodes);
        content.setReducesNodes(reducesNodes);
    }
}
