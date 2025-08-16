/*package com.AI4Java.BackendAI.AI.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
public class DiagramTools {

    private static final Logger log = LoggerFactory.getLogger(DiagramTools.class);

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024)) // 5MB for images
            .build();

    @Tool(name = "create_flowchart", description = "Creates a flowchart diagram from text description using Mermaid syntax. " +
            "Perfect for processes, workflows, and decision trees. " +
            "Parameter: description - describe the flowchart or provide Mermaid syntax")
    public String create_flowchart(String description) {
        try {
            log.info("Creating flowchart diagram");

            // Convert natural language to Mermaid syntax if needed
            String mermaidCode = convertToMermaidFlowchart(description);

            return generateDiagram(mermaidCode, "mermaid", "Flowchart");

        } catch (Exception e) {
            log.error("Failed to create flowchart: {}", e.getMessage());
            return "❌ Failed to create flowchart. Please check your description and try again.";
        }
    }

    @Tool(name = "create_sequence_diagram", description = "Creates a sequence diagram showing interactions between different actors/systems. " +
            "Perfect for API flows, user journeys, and system interactions. " +
            "Parameter: description - describe the sequence or provide PlantUML syntax")
    public String create_sequence_diagram(String description) {
        try {
            log.info("Creating sequence diagram");

            String plantUmlCode = convertToPlantUMLSequence(description);

            return generateDiagram(plantUmlCode, "plantuml", "Sequence Diagram");

        } catch (Exception e) {
            log.error("Failed to create sequence diagram: {}", e.getMessage());
            return "❌ Failed to create sequence diagram. Please try again.";
        }
    }

    @Tool(name = "create_organization_chart", description = "Creates an organizational chart or hierarchy diagram. " +
            "Perfect for company structures, reporting relationships, and hierarchies. " +
            "Parameter: description - describe the organization structure")
    public String create_organization_chart(String description) {
        try {
            log.info("Creating organization chart");

            String mermaidCode = convertToMermaidOrgChart(description);

            return generateDiagram(mermaidCode, "mermaid", "Organization Chart");

        } catch (Exception e) {
            log.error("Failed to create organization chart: {}", e.getMessage());
            return "❌ Failed to create organization chart. Please try again.";
        }
    }

    @Tool(name = "create_gantt_chart", description = "Creates a Gantt chart for project timelines and task scheduling. " +
            "Perfect for project management and timeline visualization. " +
            "Parameter: description - describe the project tasks and timeline")
    public String create_gantt_chart(String description) {
        try {
            log.info("Creating Gantt chart");

            String mermaidCode = convertToMermaidGantt(description);

            return generateDiagram(mermaidCode, "mermaid", "Gantt Chart");

        } catch (Exception e) {
            log.error("Failed to create Gantt chart: {}", e.getMessage());
            return "❌ Failed to create Gantt chart. Please try again.";
        }
    }

    @Tool(name = "create_network_diagram", description = "Creates a network or architecture diagram using GraphViz. " +
            "Perfect for system architecture, network topology, and dependency graphs. " +
            "Parameter: description - describe the network/system structure")
    public String create_network_diagram(String description) {
        try {
            log.info("Creating network diagram");

            String graphvizCode = convertToGraphviz(description);

            return generateDiagram(graphvizCode, "graphviz", "Network Diagram");

        } catch (Exception e) {
            log.error("Failed to create network diagram: {}", e.getMessage());
            return "❌ Failed to create network diagram. Please try again.";
        }
    }

    // Core diagram generation method
    private String generateDiagram(String diagramCode, String diagramType, String title) {
        try {
            // Encode the diagram code
            String encoded = Base64.getEncoder().encodeToString(diagramCode.getBytes(StandardCharsets.UTF_8));

            // Generate diagram URL
            String diagramUrl = String.format("https://kroki.io/%s/svg/%s", diagramType, encoded);

            // Verify the diagram can be generated
            byte[] svgData = webClient.get()
                    .uri(diagramUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (svgData != null && svgData.length > 0) {
                return String.format(
                        "✅ **%s Created Successfully!**\n\n" +
                                "📊 **Diagram URL:** %s\n\n" +
                                "💡 **How to use:**\n" +
                                "• Click the URL to view your diagram\n" +
                                "• Right-click → Save to download as SVG\n" +
                                "• Copy URL to share with others\n\n" +
                                "🔧 **Diagram Code:**\n" +
                                "``````\n\n" +
                                "📝 **Note:** Diagram is ready to view and can be embedded in documents!",
                        title, diagramUrl, diagramCode
                );
            } else {
                return "❌ Failed to generate diagram. Please check your syntax.";
            }

        } catch (Exception e) {
            log.error("Failed to generate diagram: {}", e.getMessage());
            return "❌ Failed to generate diagram. Service may be temporarily unavailable.";
        }
    }

    // Natural language to diagram code converters
    private String convertToMermaidFlowchart(String description) {
        // If already looks like Mermaid code, return as-is
        if (description.contains("flowchart") || description.contains("-->") || description.contains("graph")) {
            return description;
        }

        // Simple natural language conversion (you can make this more sophisticated)
        StringBuilder mermaid = new StringBuilder("flowchart TD\n");

        // Basic pattern recognition for common flowchart elements
        if (description.toLowerCase().contains("start") && description.toLowerCase().contains("end")) {
            mermaid.append("    Start([Start])\n");
            mermaid.append("    Process[Process Data]\n");
            mermaid.append("    Decision{Decision?}\n");
            mermaid.append("    End([End])\n");
            mermaid.append("    Start --> Process\n");
            mermaid.append("    Process --> Decision\n");
            mermaid.append("    Decision -->|Yes| End\n");
            mermaid.append("    Decision -->|No| Process\n");
        } else {
            // Generic flowchart based on description
            mermaid.append("    A[").append(description.length() > 50 ? "Process" : description).append("]\n");
            mermaid.append("    B[Next Step]\n");
            mermaid.append("    C[Final Step]\n");
            mermaid.append("    A --> B\n");
            mermaid.append("    B --> C\n");
        }

        return mermaid.toString();
    }

    private String convertToPlantUMLSequence(String description) {
        if (description.startsWith("@startuml")) {
            return description;
        }

        StringBuilder plantuml = new StringBuilder("@startuml\n");
        plantuml.append("!theme plain\n\n");

        // Extract actors/participants from description
        if (description.toLowerCase().contains("user") && description.toLowerCase().contains("system")) {
            plantuml.append("participant User\n");
            plantuml.append("participant System\n");
            plantuml.append("participant Database\n\n");
            plantuml.append("User -> System: Request\n");
            plantuml.append("System -> Database: Query\n");
            plantuml.append("Database -> System: Response\n");
            plantuml.append("System -> User: Result\n");
        } else {
            // Generic sequence
            plantuml.append("participant A\n");
            plantuml.append("participant B\n\n");
            plantuml.append("A -> B: ").append(description.length() > 30 ? "Message" : description).append("\n");
            plantuml.append("B -> A: Response\n");
        }

        plantuml.append("@enduml");
        return plantuml.toString();
    }

    private String convertToMermaidOrgChart(String description) {
        if (description.contains("flowchart") || description.contains("-->")) {
            return description;
        }

        StringBuilder mermaid = new StringBuilder("flowchart TD\n");
        mermaid.append("    CEO[CEO]\n");
        mermaid.append("    CTO[CTO]\n");
        mermaid.append("    CFO[CFO]\n");
        mermaid.append("    Dev[Development Team]\n");
        mermaid.append("    Finance[Finance Team]\n");
        mermaid.append("    CEO --> CTO\n");
        mermaid.append("    CEO --> CFO\n");
        mermaid.append("    CTO --> Dev\n");
        mermaid.append("    CFO --> Finance\n");

        return mermaid.toString();
    }

    private String convertToMermaidGantt(String description) {
        if (description.startsWith("gantt")) {
            return description;
        }

        StringBuilder mermaid = new StringBuilder("gantt\n");
        mermaid.append("    title Project Timeline\n");
        mermaid.append("    dateFormat  YYYY-MM-DD\n");
        mermaid.append("    section Planning\n");
        mermaid.append("    Research    :done, des1, 2025-08-01, 2025-08-05\n");
        mermaid.append("    Design      :active, des2, 2025-08-06, 2025-08-10\n");
        mermaid.append("    section Development\n");
        mermaid.append("    Coding      :des3, after des2, 2025-08-20\n");
        mermaid.append("    Testing     :des4, after des3, 2025-08-25\n");

        return mermaid.toString();
    }

    private String convertToGraphviz(String description) {
        if (description.startsWith("digraph") || description.startsWith("graph")) {
            return description;
        }

        StringBuilder graphviz = new StringBuilder("digraph G {\n");
        graphviz.append("    rankdir=LR;\n");
        graphviz.append("    node [shape=box];\n\n");
        graphviz.append("    \"Load Balancer\" -> \"Web Server 1\";\n");
        graphviz.append("    \"Load Balancer\" -> \"Web Server 2\";\n");
        graphviz.append("    \"Web Server 1\" -> \"Database\";\n");
        graphviz.append("    \"Web Server 2\" -> \"Database\";\n");
        graphviz.append("    \"Database\" -> \"Cache\";\n");
        graphviz.append("}\n");

        return graphviz.toString();
    }
}*/

