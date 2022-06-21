package com.acorus.pom.util;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.transport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


class cConfig{


    static final String username = "CheneAntray@163.com";
    static final String password = "";
    static final String localDir = "D:\\workspace\\demo";
    static final String gitUrl  = "https://github.com/CheneAntray/demo.git";
    static final String pomPath = "D:\\workspace";
    static final String branch = "feature_20220621";

    public static Map<String,String> pathList(){
        return null;
    }


}


public class PomUtil {





    public static void main(String[] args) {
        changeBranch();
//        editVersion();
    }




    //检测分支并切换
    private static void changeBranch(){
        CredentialsProvider credential = GitUtils.createCredentialsProvider(cConfig.username, cConfig.password);
        Git git = null;
        try {
            git = GitUtils.getGit(cConfig.gitUrl, credential, cConfig.localDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
        try {
            git.checkout().setName(cConfig.branch).call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
        try {
            git.pull().call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
        GitUtils.closeGit(git);
    }

    //拉去最新代码

    //修改版本号
    private static void editVersion(){


        MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
        MavenXpp3Writer xpp3Writer = new MavenXpp3Writer();

        String pomUrl = cConfig.pomPath+"\\pom.xml";
        String versionRgex = "<version>(.*?)</version>";

        try {
            FileInputStream inputStream = new FileInputStream(new File(pomUrl));
            Model model = xpp3Reader.read(inputStream);
            Parent parent = model.getParent();
            String version = model.getVersion();
            //修改父版本依赖版本号
            if(parent!=null&&!"ifss-platform".equals(parent.getArtifactId())){
                parent.setVersion("1.0-SNAPSHOT");
            }
            //修改版本号
            if(version!=null){
                model.setVersion("1.0-SNAPSHOT");
            }
            xpp3Writer.write(new FileWriter(pomUrl),model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }





}



enum GitBranchType {
    /**
     * 本地分支
     */
    LOCAL("refs/heads/"),
    /**
     * 远程分支
     */
    REMOTE("refs/remotes/origin/");
    /**
     * 分支前缀
     */
    private String prefix;
    GitBranchType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}


class GitUtils {


    public static final Logger log =  LoggerFactory.getLogger(GitUtils.class);


    /*
     * 提交文件到仓库 (包括新增的、更改的、删除的)
     * @param git
     * @param credential
     * @param filePattern
     * @param commitMessage
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    public static boolean commitFiles(Git git, CredentialsProvider credential, String filePattern, String commitMessage) throws GitAPIException, IOException, GitAPIException, IOException {
        //添加
        git.add().addFilepattern(filePattern).call();
        git.add().addFilepattern(filePattern).setUpdate(true).call();
        //提交
        git.commit().setMessage(commitMessage).call();
        //推送到远程
        Iterable<PushResult> pushResults = git.push().setCredentialsProvider(credential).call();
        printCommitPushResult(pushResults, git.getRepository().getBranch(), commitMessage);
        return true;
    }

    /*
     * 打印提交文件的日志
     * @param results
     */
    private static void printCommitPushResult(Iterable<PushResult> results, String branchName, String commitMessage) {
        log.info("git add && git commit -m '{}'", commitMessage);
        log.info("git push");
        for (PushResult result : results) {
            RemoteRefUpdate remoteRefUpdate  = result.getRemoteUpdate(GitBranchType.LOCAL.getPrefix() + branchName);
            if (RemoteRefUpdate.Status.OK.equals(remoteRefUpdate.getStatus())) {
                log.info("remote: " + result.getMessages().substring(0, result.getMessages().length() - 1));
            } else {
                log.error("remote: " + result.getMessages());
            }
            log.info("To {}", result.getURI());
        }
    }
    /*
     * 删除分支
     * @param git
     * @param credential
     * @param branchName
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    public static boolean removeBranch(Git git, CredentialsProvider credential, String branchName) throws GitAPIException, IOException {
        // master分支不能删除
        if ("master".equals(branchName)) {
            return false;
        }

        String oldBranch = git.getRepository().getBranch();
        //如果要删除的分支等于当前分支，切换到master
        if (oldBranch.equals(branchName)) {
            git.checkout().setName("master").call();
        }
        git.branchDelete().setBranchNames(GitBranchType.LOCAL.getPrefix() + branchName).setForce(true).call();
        git.branchDelete().setBranchNames(GitBranchType.REMOTE.getPrefix() + branchName).setForce(true).call();
        //推送到远程
        String branchFullName = GitBranchType.LOCAL.getPrefix() + branchName;
        RefSpec refSpec = new RefSpec(":" + branchFullName).setForceUpdate(true);
        Iterable<PushResult> results =  git.push().setCredentialsProvider(credential).setRefSpecs(refSpec).call();
        printRemoveBranchResult(results, branchFullName, branchName);
        return true;
    }

    /*
     * 打印删除分支的日志
     * @param results
     * @param branchFullName
     */
    private static void printRemoveBranchResult(Iterable<PushResult> results, String branchFullName, String branchName) {
        log.info("git push origin --delete {}", branchName);
        for (PushResult result : results) {
            RemoteRefUpdate remoteRefUpdate  = result.getRemoteUpdate(branchFullName);
            if (CheckoutResult.Status.OK.equals(remoteRefUpdate.getStatus())) {
                log.info("remote: " + result.getMessages().substring(0, result.getMessages().length() - 1));
            } else {
                log.error("remote: " + result.getMessages());
            }
            log.info("To {}", result.getURI());
            log.info("- [deleted]    {}", branchName);
        }
    }
    /*
     * 创建分支
     * @param git
     * @param branchName
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    public static String createBranch(Git git, CredentialsProvider credential, String branchName) throws GitAPIException, IOException {
        //如果本地存在分支,直接返回
        if (getBranches(git, GitBranchType.LOCAL).contains(branchName)) {
            return branchName;
        }
        //如果远端存在分支，则创建本地分支
        if (getBranches(git, GitBranchType.REMOTE).contains(branchName)) {
            String oldBranch = git.getRepository().getBranch();
            git.checkout().setName(branchName).setCreateBranch(true).call();
            git.checkout().setName(oldBranch).call();
            return branchName;
        }
        //新建分支
        git.branchCreate().setName(branchName).call();
        String oldBranch = git.getRepository().getBranch();
        git.checkout().setName(branchName).call();
        //推送到远程
        git.push().setCredentialsProvider(credential).call();
        git.checkout().setName(oldBranch).call();
        return branchName;
    }

    /*
     * 获取所有分支
     * @param git
     * @param branchType 分支类型，分为本地分支和远程分支
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    public static List<String> getBranches(Git git, GitBranchType branchType) throws GitAPIException, IOException {
        if (GitBranchType.LOCAL.equals(branchType)) {
            List<Ref> refs = git.branchList().call();
            return refs.stream().map(ref -> ref.getName().substring(GitBranchType.LOCAL.getPrefix().length()))
                    .collect(Collectors.toList());
        } else {
            List<Ref> refs = git.getRepository().getRefDatabase().getRefs();
            return refs.stream().filter(item -> !(item instanceof SymbolicRef))
                    .filter(item -> item.getName().startsWith(GitBranchType.REMOTE.getPrefix()))
                    .map(ref -> ref.getName().substring(GitBranchType.REMOTE.getPrefix().length()))
                    .collect(Collectors.toList());
        }
    }

    /*
     * 获取git对象
     * @param gitUrl git的http路径
     * @param credentialsProvider 认证
     * @param localPath 本地路径
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    public static Git getGit(String gitUrl, CredentialsProvider credentialsProvider, String localPath) throws IOException, GitAPIException {
        if (new File(localPath).exists() ) {
            return Git.open(new File(localPath));
        } else {
            return Git.cloneRepository().setCredentialsProvider(credentialsProvider).setURI(gitUrl)
                    .setDirectory(new File(localPath)).call();
        }
    }

    /*
     * 关闭git
     * @param git
     */
    public static void closeGit(Git git) {
        git.close();
    }
    /*
     * 创建Git认证信息
     * @param username
     * @param password
     * @return
     */
    public static CredentialsProvider createCredentialsProvider(String username, String password) {
        return new UsernamePasswordCredentialsProvider(username, password);
    }



}
