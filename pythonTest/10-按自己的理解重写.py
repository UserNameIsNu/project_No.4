import ollama
import time
import json
from ollama import chat
from torch.cpu import stream


class SelfVersion:
    def __init__(self):
        self.illation_model = "qwen2.5:3b"                  # 推理模型
        self.summary_model = "deepseek-r1:8b"               # 总结模型
        self.context = []                                   # 上下文
        self.context_config = {
            'max_context_history_length': 10,               # 最大上下文历史长度
        }
        self.tool_methods = self.tool_method_definition()   # 可用工具方法
        self.streaming_output_char_delay = 0.1              # 流式输出字符延迟
        self.memory_path = "D:\memory.txt"                  # 记忆地址
        self.memory = {}                                    # 记忆

        # 加载ollama
        self.load_ollama()
        # 加载记忆
        self.load_memory()

    # 加载ollama
    def load_ollama(self):
        try:
            response = ollama.list()
            print("ollama连接正常")
            model_num = 0
            for item in response.models:
                print(item.model)
                model_num += 1
            if model_num == 0:
                print("ollama没有可用模型")
            return True
        except:
            print("ollama连接失败")
            return False

    # 加载记忆
    def load_memory(self):
        try:
            # 读取记忆
            with open(self.memory_path, 'r', encoding="utf-8") as f:
                self.memory = json.load(f)
        except:
            # 否则仅有空结构
            self.memory = {
                "long_time_target": [],
                "cause": [],
                "tools_to_assist": []
            }

    # 保存记忆
    def save_memory(self):
        try:
            with open(self.memory_path, 'w', encoding="utf-8") as f:
                json.dump(self.memory, f, ensure_ascii=False, indent=2)
        except Exception as e:
            print(f"无法保存记忆：{e}")

    # 工具方法定义
    def tool_method_definition(self):
        def get_time():
            return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())

        def read_file(file_path: str):
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
                return content
            except Exception as e:
                return f"读取失败：{e}"

        def write_file(file_path: str, content: str, mode: str):
            try:
                with open(file_path, mode, encoding='utf-8') as f:
                    f.write(content)
                return f"文件 '{file_path}' 写入成功"
            except Exception as e:
                return f"写入文件失败：{e}"

        return {
            'get_time': get_time,
            'read_file': read_file,
            'write_file': write_file
        }

    # 工具方法结构
    def tool_method_structure(self, functions):
        # 获取时间的方法
        if functions.__name__ == 'get_time':
            # 返回类型，没有参数
            return {"type": "object", "properties": {}}
        # 读取指定文件
        elif functions.__name__ == 'read_file':
            return {
                "type": "object",
                "properties": {
                    "file_path": {
                        "type": "string", "description": "文件路径"
                    }
                },
                "required": ["file_path"]
            }
        # 写入指定文件
        elif functions.__name__ == 'write_file':
            return {
                "type": "object",
                "properties": {
                    "file_path": {
                        "type": "string", "description": "文件路径"
                    },
                    "content": {
                        "type": "string", "description": "内容"
                    },
                    "mode": {
                        "type": "string", "description": "操作类型（'w'为覆盖写入，'a'为追加写入）"
                    }
                },
                "required": ["file_path", "content", "mode"]
            }
        # 若试图调用不存在的方法
        return {
            # 直接返回空结构
            "type": "object",
            "properties": {}
        }

    # 上下文管理（角色有：user用户，system系统，ai模型输出，tool工具方法返回）
    def manager_context(self, message):
        # 加入
        self.context.append({
            "role": "user",
            "content": message
        })
        # 检查长度与超出裁剪
        if len(self.context) > self.context_config['max_context_history_length']:
            self.context = (
                # 保留前两条（第一条是系统提示词，第二条是记忆）
                self.context[:2] +
                # 然后就是从最新的开始，往后最大数量减2条上下文记录
                self.context[-(self.context_config['max_context_history_length'] - 2):]
            )
        # 重新加入记忆（因为这个方法是在对话轮中被调用，而记忆在每次对话后均可能被更新，故每轮对话前都需要重新读取加入）
        memory = json.dumps(self.memory, ensure_ascii=False)
        self.context.insert(
            1,
            {
                "role": "system",
                "content": f"以下为你先前自己总结的经验记忆，必须在推理回答时参考这些内容：{memory}"
            }
        )

    # 加入提示词（这玩意只加入一次即可，所以不用合并到上下文管理方法里面）
    def add_prompt_words(self):
        prompt_text = """
                请用中文回答。
                回答必须简洁直接，不做额外解释，不做科普，不补充未被要求的背景信息，不评价问题本身，不说明自身能力或限制。
                允许进行轻微的社交化回应。
    
                你可以使用以下工具：
                - get_time: 获取当前系统时间
                - read_file: 读取指定文件内容
                - write_file: 写入指定文件内容（追加或覆盖）
    
                规则：
                1. 当用户询问时间、计算或查看文件内容时，必须使用相应工具。  
                2. 工具调用后，不要在同一条消息中输出任何解释性文字。  
                3. 工具返回内容会以 role="tool" 的纯文本形式出现。接收到工具结果后，你的下一条回复只需给出一句简短、直接的答案，不做扩展，不重复工具内容以外的信息。  
                4. 工具结果呈现必须为纯文本，不使用任何标签、XML、Markdown 代码块或其他包装格式。  
                5. 不要合并多个动作。如果需要计算和看时间，先调用 math，再调用 time，各自按流程回答。  
                6. 当用户直接给出某个时间（例如 “现在是 2025年12月3日10点20分”），将此视为普通输入，不讨论其是否真实或可能，也不额外补充说明。  
                7. 当用户要求执行不可能的操作时，可以用自然、轻松的方式回应。
                8. 永远不要在回答中加入“作为 AI”“我不能”“实际上无法”等自述式句子，只需简短拒绝或简短回答。
    
                示例格式（必须遵守）：
                用户：现在几点？
                助手：（发起 get_time 工具调用）
                工具：2025-12-03 10:22:19
                助手：现在是 10:22:19
    
                用户：请读取 D:\新建 文本文档.txt
                助手：（调用 read_file 工具）
                工具：<文件内容>
                助手：<简短回答>
    
                保持以上行为规范。
                """
        self.context.insert(
            0,
            {
                "role": "system",
                "content": prompt_text
            }
        )

    # 模拟流式输出
    def simulate_stream_output(self, content):
        for char in content:
            print(char, end="", flush=True)
            time.sleep(self.streaming_output_char_delay)

    # 总结经验
    def summary_experience(self, user_massage, assistant_message, ai_message, use_tools):
        # print("\n\n\n")
        # print(user_massage)
        # print(assistant_message)
        # print(ai_message)
        # print(use_tools)
        # 提示词
        prompt = f"""
                从以下对话里判断是否存在值得写入长期记忆的新信息。
                请只输出 JSON，不要自然语言。

                对话：
                用户：{user_massage}
                助手：{assistant_message}
                ai回复：{ai_message}
                使用的工具：{use_tools}

                请分析并生成：
                {{
                    "long_time_target": [],
                    "cause": [],
                    "tools_to_assist": []
                }}
                """
        # 总结模型推理
        res = ollama.chat(
            model = self.summary_model,
            messages=[
                {"role": "system", "content": "你是记忆提炼器，只输出 JSON，不解释"},
                {"role": "user", "content": prompt}
            ]
        )
        try:
            # print(res.message.thinking)
            # print(res.message.content)
            # print("\n\n\n")
            # 拉出返回
            data = json.loads(res['message']['content'])
            # 找找有没有这些玩意（就是记忆结构的三个必须字段）
            for key in ["long_time_target", "cause", "tools_to_assist"]:
                # 若有
                if key in data:
                    # 拉出来
                    for item in data[key]:
                        # 再看看这个记忆是否已存在
                        if item not in self.memory[key]:
                            # 新的，那么保存
                            self.memory[key].append(item)
            # 持久化记忆
            self.save_memory()
        except Exception as e:
            print(f"总结经验出问题了：{e}")

    # 展示上下文
    def show_context(self):
        for item in self.context:
            print(item)

    # 聊天流（处理单轮对话，还有模型的工具调用请求）
    def chat_stream(self, message):
        try:
            # 加入新消息至上下文
            self.manager_context(message)
            # 最终响应
            final_response = ""

            # 调用循环
            while True:
                # 让模型推理，拿到结果
                response = chat(
                    model = self.illation_model,
                    messages = self.context,
                    tools = [
                        {
                            "type": "function",
                            "function": {
                                "name": name,
                                "description": functions.__doc__ or "",
                                "parameters": self.tool_method_structure(functions)
                            }
                        } for name, functions in self.tool_methods.items()
                    ],
                    stream = True # 使用流式输出
                )

                # 是否需要使用工具方法
                need_tools = False
                # 要用哪些工具方法
                tools = []
                # 模型返回
                model_response = ""
                # 助手消息
                assistant = ""

                # 处理推理结果
                for block in response:
                    # 若模型返回的消息里表示需要调用工具方法，且给出了调用清单
                    if hasattr(block.message, 'tools') and block.message.tools:
                        # 确认需要使用工具方法，并保存调用清单
                        need_tools = True
                        tools = block.message.tools

                    # 若模型返回的消息里有回复消息，且对应字段里确实有东西
                    if hasattr(block.message, 'content') and block.message.content:
                        # 输出回复，并保存响应
                        content = block.message.content
                        self.simulate_stream_output(content)
                        model_response += content

                    if hasattr(block.message, 'assistants') and block.message.assistants:
                        assistant = block.message.assistants

                if not need_tools:
                    final_response = model_response
                    self.context.append({
                        "role": "ai",
                        "content": final_response
                    })
                    break

                self.context.append({
                    "role": "assistant",
                    "content": assistant
                })

                for tool in tools:
                    tool_name = tool.function.name
                    tool_args = tool.function.arguments

                    if tool_name in self.tool_methods:
                        try:
                            if isinstance(tool_args, str):
                                parser_args = json.loads(tool_args)
                            else:
                                parser_args = tool_args

                            if len(tool_args) < 1:
                                tool_result = self.tool_methods[tool_name]()
                            else:
                                tool_result = self.tool_methods[tool_name](**parser_args)

                            tool_massage = {
                                "role": "tool",
                                "content": str(tool_result)
                            }
                            self.context.append(tool_massage)
                            print(f"工具返回：{str(tool_result)}")
                        except Exception as e:
                            print(f"工具炸了：{e}")
                            tool_massage = {
                                "role": "tool",
                                "content": e
                            }
                            self.context.append(tool_massage)
                    else:
                        print(f"未知工具：{tool_name}")
                        tool_massage = {
                            "role": "tool",
                            "content": f"未知工具：{tool_name}"
                        }
                        self.context.append(tool_massage)
            # 总结经验
            self.summary_experience(message, assistant, final_response, tools)
            return final_response
        except Exception as e:
            print(f"有东西坏了：{e}")
            return e


# 主程序
def main():
    # 初始化
    test = SelfVersion()
    # 装载提示词
    test.add_prompt_words()

    print(test.show_context())
    print("\n\n\n📝 可用命令:")
    print("  - 'show': 显示上下文信息")
    print("  - 'exit': 退出程序")

    while True:
        user_input = input("/: ").strip()

        if user_input.lower() in 'exit':
            break
        elif user_input.lower() == 'show':
            test.show_context()
            continue
        elif not user_input:
            continue

        test.chat_stream(user_input)

# 启动
if __name__ == "__main__":
    main()
