import com.agentbill.AgentBill;
import java.util.*;

public class OpenAIBasic {
    public static void main(String[] args) {
        // Initialize AgentBill with your API key
        AgentBill.Config config = new AgentBill.Config(
            System.getenv().getOrDefault("AGENTBILL_API_KEY", "your-api-key")
        )
            .setBaseUrl(System.getenv("AGENTBILL_BASE_URL"))
            .setCustomerId("customer-123")
            .setDebug(true);

        AgentBill agentbill = AgentBill.init(config);

        // Wrap your OpenAI client
        AgentBill.OpenAIWrapper openai = agentbill.wrapOpenAI();

        // Use OpenAI normally - tracking is automatic!
        List<Map<String, String>> messages = Arrays.asList(
            Map.of("role", "system", "content", "You are a helpful assistant."),
            Map.of("role", "user", "content", "What is the capital of France?")
        );

        Map<String, Object> response = openai.chatCompletion("gpt-4o-mini", messages);
        System.out.println(response);

        // All usage (tokens, cost, latency) is automatically tracked to your AgentBill dashboard
    }
}
