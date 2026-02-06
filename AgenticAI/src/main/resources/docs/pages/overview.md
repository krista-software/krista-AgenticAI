# Overview

## Extension Purpose

The Agentic AI Extension is designed to enhance information provided from CAM (Conversational AI Management) using advanced Large Language Model (LLM) capabilities. This extension provides intelligent processing for conversational AI workflows using cloud-based LLM providers.

## Key Features

### Cloud LLM Integration
* **Supported Providers**: OpenAI and Claude
* **Reliable Processing**: Uses cloud-based LLM providers for consistent performance
* **Comprehensive Logging**: All API calls and responses are logged for troubleshooting
* **Future Ready**: Infrastructure in place for Krista LLM integration when available

### Supported Actions
* **SUMMARIZE_ASK**: Summarizes and asks questions based on current form state
* **SUMMARIZE_INFORM**: Provides informational summaries to users
* **EXTRACT_VARIABLES**: Extracts and validates variables from user input

### Advanced Processing Capabilities
* **Field Conversion**: Converts between Krista field formats and LLM-compatible formats
* **Response Validation**: Validates LLM responses against expected field structures
* **Error Handling**: Graceful error handling with detailed logging
* **Rate Limiting**: Handles API rate limits with appropriate retry strategies

## Architecture

### Core Components
* **CatalogRequestHandler**: Main entry point for processing requests
* **StepHandlerFactory**: Creates appropriate handlers based on action type
* **LLMServiceWithFallback**: Manages LLM provider communication
* **Field Converters**: Handle conversion between different field formats
* **Response Validators**: Ensure LLM responses meet expected criteria

### Supported LLM Providers
* **OpenAI**: gpt-4.1-mini
* **Claude**: claude-sonnet-4-20250514

**Note**: Krista LLM support is available but not currently exposed in the setup interface.

## Integration Points

The extension integrates with:
* **CAM System**: Receives form data and user interactions
* **Krista Platform**: Uses platform field definitions and validation
* **External LLM APIs**: Communicates with cloud-based LLM providers
* **Workspace Configuration**: Respects workspace-level settings and permissions
