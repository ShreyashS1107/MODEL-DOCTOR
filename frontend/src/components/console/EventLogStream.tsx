"use client";

import React, { useState } from "react";
import { LogEntry } from "@/types/diagnostics";

interface EventLogStreamProps {
  logs: LogEntry[];
  onClearLogs?: () => void;
}

export const EventLogStream: React.FC<EventLogStreamProps> = ({
  logs,
}) => {
  const [filterLevel, setFilterLevel] = useState<"ALL" | "CRIT" | "WARN" | "INFO">("ALL");
  const [isExpanded, setIsExpanded] = useState(false);

  const filteredLogs = logs.filter((l) => {
    if (filterLevel === "ALL") return true;
    return l.level === filterLevel;
  });

  const getLevelBadgeClass = (level: LogEntry["level"]) => {
    switch (level) {
      case "CRIT":
        return "bg-[#F18B78] text-[#211D29] font-bold";
      case "WARN":
        return "bg-[#E8B66D] text-[#211D29] font-bold";
      case "EVAL":
        return "bg-[#B7D7A8] text-[#211D29] font-bold";
      case "INFO":
      default:
        return "bg-[#332B3C] text-[#B7ACBD]";
    }
  };

  return (
    <div
      className={`bg-[#2A2432] border-t border-[#443849] flex flex-col shrink-0 select-none font-mono text-xs transition-all ${
        isExpanded ? "h-64" : "h-36"
      }`}
    >
      {/* Console Toolbar Header */}
      <div className="h-8 bg-[#211D29] border-b border-[#443849] px-3 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-[#B7ACBD] uppercase font-bold tracking-wider">
            DIAGNOSTIC EVENT STREAM
          </span>
          <span className="text-[10px] text-[#8E8295]">
            ({filteredLogs.length} entries)
          </span>
        </div>

        {/* Filter Buttons */}
        <div className="flex items-center gap-2 text-[10px]">
          <div className="flex items-center bg-[#332B3C] border border-[#443849] rounded-sm">
            <button
              onClick={() => setFilterLevel("ALL")}
              className={`px-2 py-0.5 cursor-pointer ${
                filterLevel === "ALL" ? "bg-[#443849] text-[#F4EFF6] font-bold" : "text-[#8E8295] hover:text-[#B7ACBD]"
              }`}
            >
              ALL
            </button>
            <button
              onClick={() => setFilterLevel("CRIT")}
              className={`px-2 py-0.5 cursor-pointer ${
                filterLevel === "CRIT" ? "bg-[#443849] text-[#F18B78] font-bold" : "text-[#8E8295] hover:text-[#B7ACBD]"
              }`}
            >
              CRIT
            </button>
            <button
              onClick={() => setFilterLevel("WARN")}
              className={`px-2 py-0.5 cursor-pointer ${
                filterLevel === "WARN" ? "bg-[#443849] text-[#E8B66D] font-bold" : "text-[#8E8295] hover:text-[#B7ACBD]"
              }`}
            >
              WARN
            </button>
            <button
              onClick={() => setFilterLevel("INFO")}
              className={`px-2 py-0.5 cursor-pointer ${
                filterLevel === "INFO" ? "bg-[#443849] text-[#B7D7A8] font-bold" : "text-[#8E8295] hover:text-[#B7ACBD]"
              }`}
            >
              INFO
            </button>
          </div>

          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="px-2 py-0.5 bg-[#332B3C] hover:bg-[#3D3448] border border-[#443849] text-[#B7ACBD] hover:text-[#F4EFF6] rounded-sm cursor-pointer"
          >
            {isExpanded ? "COLLAPSE" : "EXPAND"}
          </button>
        </div>
      </div>

      {/* Log Entry Rows */}
      <div className="flex-1 overflow-y-auto p-2 space-y-1 bg-[#1C1824] font-mono text-[11px]">
        {filteredLogs.map((log) => (
          <div
            key={log.id}
            className="flex items-start gap-2 hover:bg-[#2A2432] px-1.5 py-0.5 leading-tight rounded-xs"
          >
            <span className="text-[#8E8295] tabular-nums shrink-0">{log.timestamp}</span>
            <span className={`px-1 py-0.2 text-[9px] shrink-0 rounded-xs ${getLevelBadgeClass(log.level)}`}>
              {log.level}
            </span>
            <span className="text-[#B7ACBD] shrink-0 w-36 truncate font-semibold">
              [{log.module}]
            </span>
            <span className="text-[#F4EFF6] break-all">{log.message}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
