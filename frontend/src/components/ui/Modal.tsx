"use client";

import React from "react";

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 select-none">
      <div className="relative w-full max-w-lg bg-[#0c0e14] border border-[#1f2533] shadow-2xl">
        {/* Modal Header */}
        <div className="h-9 px-3 bg-[#090b0e] border-b border-[#1f2533] flex items-center justify-between">
          <div>
            <span className="text-xs font-mono font-bold text-white uppercase">
              {title}
            </span>
          </div>
          <button
            onClick={onClose}
            className="text-xs font-mono text-[#64748b] hover:text-white px-1.5 py-0.5 hover:bg-[#1f2533] cursor-pointer"
          >
            [ESC]
          </button>
        </div>

        {/* Subtitle if present */}
        {subtitle && (
          <div className="px-3 py-1.5 bg-[#0e1117] border-b border-[#1f2533] text-[10px] font-mono text-[#64748b]">
            {subtitle}
          </div>
        )}

        {/* Modal Content */}
        <div className="p-3">{children}</div>
      </div>
    </div>
  );
};
