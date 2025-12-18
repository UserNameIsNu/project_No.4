/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.tools.generalTools;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件IO工具类<br/>
 * 封装了文件操作与IO操作相关的方法。
 */
@Component
public class FileIO {
    /**
     * 异常日志地址
     */
    @Value("${great-plan.error-log.path}")
    private String errorLogPath;

    /**
     * 调用链打印深度
     */
    @Value("${great-plan.error-log.stackTrace-deep}")
    private String stackTraceDeep;

    /**
     * 文本文件表</br>
     * 就是可以被打开转成String的文件。
     */
    private List<String> contextFile = new ArrayList<>(List.of(
            "txt", "md", "json", "xml", "html", "htm", "css", "js", "jsx", "ts", "tsx",
            "py", "java", "cpp", "c", "h", "cs", "php", "rb", "go", "rs", "sql"
    ));

    /**
     * 文件收集器（指定类型文件）<br/>
     * 用于从指定目录内递归收集所有指定格式的文件，以集合形式返回。
     * @param files 目标目录
     * @param format 目标文件格式（.后缀名）
     * @return 收集到的文件集合
     */
    public static List<File> fileCollector(File files, String format) {
        List<File> outcome = null;
        try {
            // 打开目录并递归遍历
            outcome = Files.walk(files.getAbsoluteFile().toPath())
                    // 仅抓取指定格式的文件
                    .filter(p -> p.toString().endsWith(format))
                    // 加入一个临时集合
                    .map(Path::toFile)
                    // 转换为List
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outcome;
    }

    /**
     * 文件收集器（全部文件）</br>
     * 用于将指定目录下的所有直接子成员拉出来。
     * 不递归，结果仅为子成员名。
     * @param filePath 目标目录路径
     * @return 子成员名集合
     */
    public List<String> fileCollector_All(String filePath) {
        // 子成员名集合
        List<String> filesNameList = new ArrayList<>();
        // 目录路径
        Path path =  Paths.get(filePath).toAbsolutePath();

        // 遍历目录内的子成员
        try (Stream<Path> stream = Files.list(path)) {
            stream.forEach(sonPath -> {
                // 拉出名字
                filesNameList.add(sonPath.getFileName().toString());
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return filesNameList;
    }

    /**
     * 获取当前运行设备的根目录<br/>
     * 如windows，我这电脑就有C，D两个盘根。
     * @return 根目录集合
     */
    public List<String> getRoot() {
        // 根目录集合
        List<String> rootPaths = new ArrayList<>();
        // 获取文件对象的所有根（似乎是会直接取到文件系统的）
        File[] roots = File.listRoots();
        // 遍历所有根
        for (File root : roots) {
            // 把路径拎出来
            String path = root.getAbsolutePath();
            // 把盘符剪出来（C:/变成C:）
            if (path.contains("\\")) {
                path = path.substring(0, 2) + "/";
            }
            // 塞进集合
            rootPaths.add(path);
        }
        return rootPaths;
    }

    /**
     * 获取文件后缀<br/>
     * 根据文件路径切割，取最后面的后缀段。
     * 路径不管是绝对路径，还是单个文件名都可以。
     * 直接切割带来的自信。
     * @param filePath 文件路径
     * @return 文件后缀
     */
    public String getFileExtension(String filePath) {
        int dot = filePath.lastIndexOf(".");
        return (dot == -1) ? "" : filePath.substring(dot + 1);
    }

    /**
     * 生成ID<br/>
     * 根据文件或目录的路径创建哈希ID.
     * @param file 文件（目录）目标
     * @return 这个文件（目录）的ID
     */
    public String generateId(File file) {
        return Integer.toHexString(file.getAbsolutePath().hashCode());
    }

    /**
     * 删除文件后缀<br/>
     * 根据路径切割，获取没有带文件后缀的路径。
     * @param name 文件路径
     * @return 没后缀的文件路径
     */
    public String stripExtension(String name) {
        int dot = name.lastIndexOf(".");
        return (dot == -1) ? name : name.substring(0, dot);
    }

    /**
     * 异常写入<br/>
     * 用于将异常写进异常日志
     * @param e 异常实例
     */
    public void exceptionWrite(Throwable e) {
        // 创建字符串构建器
        StringBuilder sb = new StringBuilder();

        // 构建内容
        sb.append("\n================= " + LocalDateTime.now() + " =================\n");
        sb.append("错误类型：").append(e.getClass().getName()).append("\n");
        sb.append("错误消息：").append(e.getMessage()).append("\n");
        // 错误位置（其实就是调用链的最末节点）
        if (e.getStackTrace().length > 0) {
            StackTraceElement top = e.getStackTrace()[0];
            sb.append("位置：")
                    .append(top.getClassName())
                    .append(".")
                    .append(top.getMethodName())
                    .append(" (")
                    .append(top.getFileName())
                    .append(":")
                    .append(top.getLineNumber())
                    .append(")\n");
        }
        // 输出完整调用链
        sb.append("调用链：\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        // 根据配置文件决定调用链的打印深度
        Integer deep;
        // 全量打印
        if (stackTraceDeep.equals("all")) {
            deep = stackTrace.length;
        // 定量打印
        } else {
            deep = Integer.parseInt(stackTraceDeep);
        }
        for (int i = 0; i < deep; i++) {
            StackTraceElement element = stackTrace[i];
            sb.append("  [").append(i).append("] ")
                    .append(element.getClassName())
                    .append(".")
                    .append(element.getMethodName())
                    .append("(")
                    .append(element.getFileName())
                    .append(":")
                    .append(element.getLineNumber())
                    .append(")\n");
        }
        sb.append("========================================================\n");

        // 定义文件名（包括路径）
        String path = errorLogPath + "/error.log";
        // 创建并追加日志记录
        createFileByName(path);
        addToFile(sb, path);
    }

    /**
     * 在指定位置创建目录<br/>
     * 允许递归创建深度目录。
     * 避免预期目录和实际目录之间没有直接或间接的线性从属关系，导致无法接续。
     * @param path 文件地址
     */
    public void createFileByName(String path) {
        // 打开地址
        Path logFile = Paths.get(path);
        try {
            // 若目录不存在就创建
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 在指定位置创建文件</br>
     * 会一并判断目标路径是否存在，若不存在就会把父级目录一路创建出来。
     * @param path 文件路径
     * @return 是否成功
     */
    public Boolean createFile(String path) {
        // 转文件对象
        File file = new File(path);

        // 取父目录
        File parent = file.getParentFile();
        // 不在就创建
        if (!parent.exists()) {
            parent.mkdirs();
        }

        try {
            // 是否成功
            return file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 追加内容至指定文件
     * @param content 内容
     * @param path 文件地址
     */
    public void addToFile(StringBuilder content, String path) {
        // 打开地址
        Path logFile = Paths.get(path);
        try {
            // 写入
            Files.writeString(
                    logFile, // 写进这里
                    content, // 写这些东西进去
                    StandardOpenOption.CREATE, // 若文件不存在就先创建
                    StandardOpenOption.APPEND // 追加模式
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取指定类的绝对路径集合<br/>
     * 任意一组类的绝对路径集合。
     * @param filePaths 类的class对象
     * @return File对象集合
     */
    public List<File> getTargetFileABPaths(List<Class<?>> filePaths) {
        List<File> files = new ArrayList<>();
        for (Class clazz : filePaths) {
            URL url = clazz.getResource(clazz.getSimpleName() + ".class");
            try {
                File file = new File(url.toURI());
                files.add(file);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return files;
    }

    /**
     * 获取文件文本</br>
     * 打开指定文件，并获取其中所有文本内容。
     * @param filePath 文件路径
     * @return 内容
     */
    public String getFileContent(String filePath) {
        // 转为地址
        Path path = Paths.get(filePath);

        // 切后缀
        String[] split = path.getFileName().toString().split("\\.");
        int splitLength = split.length;

        // 判断是否为文本文件
        if(!contextFile.contains(split[splitLength-1])) {
            return "似乎不是文本文件：" + split[splitLength-1];
        }

        try {
            // 读取
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 写入指定文本文件</br>
     * 将文本写入指定文件。
     * @param filePath 文件路径
     * @param content 内容
     * @param inputType 写入模式（append：追加，write：覆盖）
     * @return 是否成功
     */
    public String writeFileContent(String filePath, String content, String inputType) {
        // 转为地址
        Path path = Paths.get(filePath);

        // 切后缀
        String[] split = path.getFileName().toString().split("\\.");
        int splitLength = split.length;

        // 判断是否为文本文件
        if(!contextFile.contains(split[splitLength-1])) {
            return "似乎不是文本文件：" + split[splitLength-1];
        }

        // 写入
        try {
            if(inputType.equals("append")) {
                Files.write(path, new ArrayList<>(List.of(content)),  StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else if(inputType.equals("write")) {
                Files.write(path, new ArrayList<>(List.of(content)),  StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return "正常";
    }

    /**
     * 写入记忆
     * @param user 用户称呼
     * @param you 你的称呼
     * @param preferences 用户偏好
     * @param history 对话历史
     * @param experiences 总结的经验
     * @return 是否成功
     */
    public String writeMemory(String user, String you, String preferences, String history, String experiences) {
        // 转为地址
        Path path = Paths.get("src/main/java/com/ljf/greatplan/docking/plugins/aiSecretary/memory.txt");

        // 切后缀
        String[] split = path.getFileName().toString().split("\\.");
        int splitLength = split.length;

        // 判断是否为文本文件
        if(!contextFile.contains(split[splitLength-1])) {
            return "似乎不是文本文件：" + split[splitLength-1];
        }

        // 基本记忆
        List<String> base = new ArrayList<>(List.of(
                "\t\"user\": \"" + user + "\",",
                "\t\"you\": \"" + you + "\",",
                "\t\"preferences\": \"" + preferences + "\","
        ));

        // 事件记忆
        List<String> event = new ArrayList<>(List.of(
                "\t\t{",
                "\t\t\t\"experiences\": \"" + experiences + "\",",
                "\t\t\t\"history\": \"" + history + "\",",
                "\t\t\t\"time\": \"" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\"",
                "\t\t},"
        ));

        // 覆盖基础记忆
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

            // 移除旧行
            lines.subList(1, 4).clear();

            // 插入新行
            lines.addAll(1, base);
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 追加事件记忆
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

            // 插入新行
            lines.addAll(lines.size() - 2, event);
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return "记忆已保存";
    }

    /**
     * 创建word文档
     * @param filePath 文件路径
     * @param paragraphs 预填内容（每个元素代表一个段落的文本内容）
     */
    public void createWord(String filePath, List<String> paragraphs) {
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(filePath)) {

            for (String text : paragraphs) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun run = p.createRun();
                run.setText(text);
            }

            doc.write(out);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 读取word文档
     * @param filePath 文件路径
     * @return 内容（每个段落对应 List 中的一个元素）
     */
    public List<String> readWord(String filePath) {
        List<String> result = new ArrayList<>();

        try (FileInputStream in = new FileInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(in)) {

            for (XWPFParagraph p : doc.getParagraphs()) {
                result.add(p.getText());
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * 修改word文档
     * @param filePath 文件路径
     * @param paragraphIndex 要修改的段落索引（从0开始）
     * @param newText 新的段落文本内容
     */
    public void updateWord(String filePath, Integer paragraphIndex, String newText) {
        try (FileInputStream in = new FileInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(in)) {

            XWPFParagraph p = doc.getParagraphs().get(paragraphIndex);

            // 1. 删除该段落中所有 run
            int runSize = p.getRuns().size();
            for (int i = runSize - 1; i >= 0; i--) {
                p.removeRun(i);
            }

            // 2. 新建一个 run 写入新文本
            XWPFRun run = p.createRun();
            run.setText(newText);

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                doc.write(out);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建excel文档
     * @param filePath 文件路径
     * @param data 内容（data 中的每个 List<String> 代表一行）
     */
    public void createExcel(String filePath, List<List<String>> data) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(filePath)) {

            XSSFSheet sheet = wb.createSheet("Sheet1");

            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i);
                List<String> rowData = data.get(i);
                for (int j = 0; j < rowData.size(); j++) {
                    row.createCell(j).setCellValue(rowData.get(j));
                }
            }
            wb.write(out);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 读取excel文档
     * @param filePath 文件路径
     * @return 内容（行 → 列 → 单元格文本）
     */
    public List<List<String>> readExcel(String filePath) {
        List<List<String>> result = new ArrayList<>();

        try (FileInputStream in = new FileInputStream(filePath);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {

            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                List<String> rowData = new ArrayList<>();
                for (Cell cell : row) {
                    rowData.add(cell.toString());
                }
                result.add(rowData);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * 修改excel文档
     * @param filePath 文件路径
     * @param rowIndex 行索引（修改第几行）
     * @param colIndex 列索引（修改第几列）
     * @param newValue 新值（指向格子里的新值）
     * @return 是否成功
     */
    public String updateExcel(
            String filePath,
            Integer rowIndex,
            Integer colIndex,
            String newValue) {

        if (filePath == null || filePath.trim().isEmpty()) {
            return "文件路径不能为空";
        }
        if (rowIndex == null || rowIndex < 0) {
            return "行索引必须为非负整数";
        }
        if (colIndex == null || colIndex < 0) {
            return "列索引必须为非负整数";
        }
        if (newValue == null) {
            return "新值不能为空";
        }

        try (FileInputStream in = new FileInputStream(filePath);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {

            Sheet sheet = wb.getSheetAt(0);
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);

            Cell cell = row.getCell(colIndex);
            if (cell == null) cell = row.createCell(colIndex);

            cell.setCellValue(newValue);

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                wb.write(out);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "成功";
    }
}
