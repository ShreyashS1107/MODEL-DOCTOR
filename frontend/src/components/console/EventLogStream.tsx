"use client";

import React, { useState } from "react";
import { LogEntry } from "@/types/diagnostics";

interface EventLogStreamProps {
  logs: LogEntry[];
  onClearLogs?: () => void;
}

export const EventLogStream: React.FC<EventLogStreamProps> = ({
  logs,
  onClearLogs,
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
        return "bg-[#ef4444] text-black font-bold";
      case "WARN":
        return "bg-[#f59e0b] text-black font-bold";
      case "EVAL":
        return "bg-[#3b82f6] text-white font-bold";
      case "INFO":
      default:
        return "bg-[#1f2533] text-[#94a3b8]";
    }
  };

  return (
    <div
      className={`bg-[#0c0e14] border-t border-[#1f2533] flex flex-col shrink-0 select-none font-mono text-xs transition-all ${
        isExpanded ? "h-64" : "h-36"
      }`}
    >
      {/* Console Toolbar Header */}
      <div className="h-8 bg-[#090b0e] border-b border-[#1f2533] px-3 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-[#64748b] uppercase font-bold tracking-wider">
            DIAGNOSTIC EVENT STREAM
          </span>
          <span className="text-[10px] text-[#64748b]">
            ({filteredLogs.length} entries)
          </span>
        </div>

        {/* Filter Buttons */}
        <div className="flex items-center gap-2 text-[10px]">
          <div className="flex items-center bg-[#141822] border border-[#222633]">
            <button
              onClick={() => setFilterLevel("ALL")}
              className={`px-2 py-0.5 ${
                filterLevel === "ALL" ? "bg-[#1f2533] text-white font-bold" : "text-[#64748b] hover:text-[#94a3b8]"
              }`}
            >
              ALL
            </button>
            <button
              onClick={() => setFilterLevel("CRIT")}
              className={`px-2 py-0.5 ${
                filterLevel === "CRIT" ? "bg-[#1f2533] text-[#ef4444] font-bold" : "text-[#64748b] hover:text-[#94a3b8]"
              }`}
            >
              CRIT
            </button>
            <button
              onClick={() => setFilterLevel("WARN")}
              className={`px-2 py-0.5 ${
                filterLevel === "WARN" ? "bg-[#1f2533] text-[#f59e0b] font-bold" : "text-[#64748b] hover:text-[#94a3b8]"
              }`}
            >
              WARN
            </button>
            <button
              onClick={() => setFilterLevel("INFO")}
              className={`px-2 py-0.5 ${
                filterLevel === "INFO" ? "bg-[#1f2533] text-[#3b82f6] font-bold" : "text-[#64748b] hover:text-[#94a3b8]"
              }`}
            >
              INFO
            </button>
          </div>

          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="px-2 py-0.5 bg-[#141822] hover:bg-[#1a202c] border border-[#222633] text-[#94a3b8] hover:text-white"
          >
            {isExpanded ? "COLLAPSE" : "EXPAND"}
          </button>
        </div>
      </div>

      {/* Log Entry Rows */}
      <div className="flex-1 overflow-y-auto p-2 space-y-1 bg-[#07090c] font-mono text-[11px]">
        {filteredLogs.map((log) => (
          <div
            key={log.id}
            className="flex items-start gap-2 hover:bg-[#10141d] px-1.5 py-0.5 leading-tight"
          >
            <span className="text-[#64748b] tabular-nums shrink-0">{log.timestamp}</span>
            <span className={`px-1 py-0.2 text-[9px] shrink-0 ${getLevelBadgeClass(log.level)}`}>
              {log.level}
            </span>
            <span className="text-[#94a3b8] shrink-0 w-36 truncate font-semibold">
              [{log.module}]
            </span>
            <span className="text-[#f1f3f8] break-all">{log.message}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
