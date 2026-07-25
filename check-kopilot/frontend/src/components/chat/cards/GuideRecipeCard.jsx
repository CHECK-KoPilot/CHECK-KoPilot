import { useState } from "react";
import { Compass, ExternalLink, Check } from "lucide-react";
import Button from "../../common/Button";

export default function GuideRecipeCard({ message }) {
  const [requested, setRequested] = useState(message.requested);

  return (
    <div className="w-full rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:p-5">
      <div className="mb-3 flex items-start gap-2">
        <Compass size={16} className="mt-0.5 shrink-0 text-accent-500" />
        <div>
          <h3 className="text-sm font-semibold text-slate-900 lg:text-base">
            {message.title}
          </h3>
          <p className="mt-0.5 text-sm text-slate-500 lg:text-[15px]">
            {message.description}
          </p>
        </div>
      </div>

      <section className="mb-3">
        <p className="mb-1.5 text-xs font-semibold text-slate-500">
          필요한 CHECK API
        </p>
        <div className="space-y-2">
          {message.apis.map((api) => (
            <div
              key={api.name}
              className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2"
            >
              <div className="flex items-center justify-between">
                <p className="text-sm text-slate-700">{api.name}</p>
                <a
                  href={api.docUrl}
                  className="flex items-center gap-1 text-xs text-accent-600 hover:underline"
                >
                  명세 보기 <ExternalLink size={11} />
                </a>
              </div>
              <code className="text-xs text-slate-400">{api.endpoint}</code>
              <div className="mt-1.5 flex flex-wrap gap-1">
                {api.params.map((p) => (
                  <span
                    key={p}
                    className="rounded bg-white px-1.5 py-0.5 text-[11px] text-slate-500 border border-slate-200"
                  >
                    {p}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="mb-4">
        <p className="mb-1.5 text-xs font-semibold text-slate-500">
          조합 레시피
        </p>
        <ol className="list-decimal space-y-1 pl-4 text-sm text-slate-600">
          {message.recipe.map((step, i) => (
            <li key={i}>{step}</li>
          ))}
        </ol>
      </section>

      <div className="flex justify-end">
        <Button
          variant={requested ? "secondary" : "primary"}
          size="sm"
          disabled={requested}
          onClick={() => setRequested(true)}
        >
          {requested ? <Check size={14} /> : null}
          {requested ? "요청 완료" : "카탈로그 추가 요청"}
        </Button>
      </div>
    </div>
  );
}
